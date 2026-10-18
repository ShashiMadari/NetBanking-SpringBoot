package com.inetbank.identity.registration;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private static final int OTP_MINUTES = 5;
    private final UserRepository users;
    private final AccountEligibilityRepository accounts;
    private final AuditEventRepository auditEvents;
    private final NotificationRepository notifications;
    private final PasswordEncoder passwordEncoder;
    private final DemoCredentialService credentials;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final boolean showDemoCredentials;

    RegistrationService(UserRepository users, AccountEligibilityRepository accounts, AuditEventRepository auditEvents, NotificationRepository notifications, PasswordEncoder passwordEncoder,
                        DemoCredentialService credentials, EmailService emailService, JwtService jwtService,
                        @Value("${app.demo.show-credentials:true}") boolean showDemoCredentials) {
        this.users = users; this.accounts = accounts; this.auditEvents = auditEvents; this.notifications = notifications; this.passwordEncoder = passwordEncoder;
        this.credentials = credentials; this.emailService = emailService; this.jwtService = jwtService; this.showDemoCredentials = showDemoCredentials;
    }

    @Transactional
    public RegistrationStartResponse startRegistration(RegistrationStartRequest request, String ipAddress) {
        String email = email(request.email());
        User user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new RegistrationException("Only existing O Bank customers can register for net banking."));
        if (!"CUSTOMER".equals(user.getRole()) || !user.isBankCustomerActive() || !name(request.fullName()).equalsIgnoreCase(user.getFullName()))
            throw new RegistrationException("Your name or email does not match an active O Bank customer record.");
        if (!accounts.hasActiveAccount(user.getId()))
            throw new RegistrationException("An active O Bank account is required before net banking registration.");
        if (!user.isNetBankingNotRegistered())
            throw new RegistrationException("Net banking registration was already started or completed for this customer.");
        String demoPassword = credentials.demoPassword();
        user.beginNetBankingRegistration(passwordEncoder.encode(demoPassword)); users.save(user);
        auditEvents.save(new AuditEvent(user.getId(), "NET_BANKING_REGISTRATION_STARTED", "USER", user.getId().toString(), ipAddress));
        notifications.save(new Notification(user.getId(), "Your O Bank net banking registration was started. Set a new password to activate your profile."));
        emailService.sendDemoPassword(email, user.getFullName(), demoPassword);
        return new RegistrationStartResponse("Your demo password has been sent. Enter it on the next page to set your own password.", "SET_PASSWORD", visible(demoPassword));
    }

    @Transactional
    public MessageResponse completeRegistration(CompleteRegistrationRequest request, String ipAddress) {
        validateNewPassword(request.newPassword(), request.confirmPassword());
        User user = getUser(email(request.email()));
        if (!user.isNetBankingPending() || !passwordEncoder.matches(request.demoPassword(), user.getPasswordHash()))
            throw new AuthException("The demo password is invalid or this registration was already completed.");
        user.activateNetBankingWithPassword(passwordEncoder.encode(request.newPassword()));
        users.save(user);
        auditEvents.save(new AuditEvent(user.getId(), "REGISTRATION_COMPLETED", "USER", user.getId().toString(), ipAddress));
        notifications.save(new Notification(user.getId(), "Your O Bank profile is active. You can now sign in."));
        return new MessageResponse("Registration complete. Please sign in with your email and new password.");
    }

    // Keep failed-attempt and audit updates when authentication is rejected.
    // Other runtime exceptions retain the normal rollback behavior.
    @Transactional(noRollbackFor = AuthException.class)
    public LoginOtpResponse sendLoginOtp(SendLoginOtpRequest request, String ipAddress) {
        return issueLoginOtp(request, ipAddress, false);
    }

    // Keep failed-attempt and audit updates when authentication is rejected.
    // Other runtime exceptions retain the normal rollback behavior.
    @Transactional(noRollbackFor = AuthException.class)
    public LoginOtpResponse resendLoginOtp(SendLoginOtpRequest request, String ipAddress) {
        return issueLoginOtp(request, ipAddress, true);
    }

    private LoginOtpResponse issueLoginOtp(
            SendLoginOtpRequest request,
            String ipAddress,
            boolean resent) {

        User user = verifyPasswordAndRole(
                request.email(),
                request.password(),
                request.loginAsAdmin(),
                ipAddress);

        String otp = credentials.otp();

        // Replaces and invalidates any previous OTP.
        user.setOtp(
                passwordEncoder.encode(otp),
                LocalDateTime.now().plusMinutes(OTP_MINUTES),
                "LOGIN");

        users.save(user);

        String auditAction = resent ? "LOGIN_OTP_RESENT" : "LOGIN_OTP_SENT";

        auditEvents.save(new AuditEvent(
                user.getId(),
                auditAction,
                "USER",
                user.getId().toString(),
                ipAddress));

        notifications.save(new Notification(
                user.getId(),
                resent
                        ? "A new login OTP was generated for your O Bank account."
                        : "A login OTP was generated for your O Bank account."));

        emailService.sendOtp(user.getEmail(), otp, "login");

        String message = resent
                ? "A new OTP was sent to your email. The previous OTP is no longer valid."
                : "An OTP was sent to your email. It expires in 5 minutes.";

        return new LoginOtpResponse(message, visible(otp));
    }

    // Keep failed-attempt and audit updates when authentication is rejected.
    // Other runtime exceptions retain the normal rollback behavior.
    @Transactional(noRollbackFor = AuthException.class)
    public AuthTokenResponse login(LoginRequest request, String ipAddress) {
        User user = verifyPasswordAndRole(request.email(), request.password(), request.loginAsAdmin(), ipAddress);
        if (!isExpectedOtp(user, request.otp(), "LOGIN")) {
            user.recordFailedAttempt(); users.save(user);
            auditEvents.save(new AuditEvent(user.getId(), "LOGIN_OTP_FAILED", "USER", user.getId().toString(), ipAddress));
            throw new AuthException("The OTP is invalid or has expired. Send a new OTP and try again.");
        }
        user.clearOtp(); user.clearFailedAttempts(); user.markLoggedIn(); users.save(user);
        auditEvents.save(new AuditEvent(user.getId(), "LOGIN_SUCCESS", "USER", user.getId().toString(), ipAddress));
        notifications.save(new Notification(user.getId(), "Successful O Bank login."));
        return new AuthTokenResponse(jwtService.create(user), "Bearer", jwtService.getExpirationSeconds(), user.getRole(), "Login successful.");
    }

    @Transactional
    public LoginOtpResponse sendResetOtp(ForgotPasswordRequest request, String ipAddress) {
        User user = users.findByEmailIgnoreCase(email(request.email())).orElse(null);
        if (user == null || !user.isAuthenticationActive()) return new LoginOtpResponse("If that email is registered, a reset OTP has been sent.", null);
        String otp = credentials.otp();
        user.setOtp(passwordEncoder.encode(otp), LocalDateTime.now().plusMinutes(OTP_MINUTES), "PASSWORD_RESET"); users.save(user);
        auditEvents.save(new AuditEvent(user.getId(), "PASSWORD_RESET_OTP_SENT", "USER", user.getId().toString(), ipAddress));
        notifications.save(new Notification(user.getId(), "A password-reset OTP was generated for your O Bank account."));
        emailService.sendOtp(user.getEmail(), otp, "password reset");
        return new LoginOtpResponse("If that email is registered, a reset OTP has been sent.", visible(otp));
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request, String ipAddress) {
        validateNewPassword(request.newPassword(), request.confirmPassword());
        User user = getUser(email(request.email()));
        if (!user.isAuthenticationActive() || !isExpectedOtp(user, request.otp(), "PASSWORD_RESET"))
            throw new AuthException("The password-reset OTP is invalid or has expired.");
        user.activateNetBankingWithPassword(passwordEncoder.encode(request.newPassword())); user.clearOtp(); users.save(user);
        auditEvents.save(new AuditEvent(user.getId(), "PASSWORD_RESET_SUCCESS", "USER", user.getId().toString(), ipAddress));
        notifications.save(new Notification(user.getId(), "Your O Bank password was reset."));
        return new MessageResponse("Password reset complete. You can now sign in.");
    }

    private User verifyPasswordAndRole(
        String rawEmail,
        String rawPassword,
        boolean loginAsAdmin,
        String ipAddress) {

    User user = users.findByEmailIgnoreCase(email(rawEmail))
            .orElseThrow(() ->
                    new AuthException("Invalid email, password, or account type."));

    if (user.unlockIfLockExpired()) {
        users.save(user);
    }

    if (user.isTemporarilyLocked()) {
        throw new AuthException(
                "This O Bank profile is temporarily locked. Try again after 30 minutes.");
    }

    boolean passwordMatches = passwordEncoder.matches(
            rawPassword,
            user.getPasswordHash());

    // Role is always obtained from the database, never assigned by the frontend.
    boolean userIsAdmin = "ADMIN".equals(user.getRole());
    boolean requestedAdminLogin = loginAsAdmin;
    boolean roleMatches = requestedAdminLogin == userIsAdmin;

    if (!passwordMatches) {
        user.recordFailedAttempt();
        users.save(user);

        auditEvents.save(new AuditEvent(
                user.getId(),
                "LOGIN_PASSWORD_FAILED",
                "USER",
                user.getId().toString(),
                ipAddress));

        throw new AuthException("Invalid email, password, or account type.");
    }

    if (!user.isAuthenticationActive()) {
        throw new AuthException("Your net banking profile is not active.");
    }

    if (!roleMatches) {
        auditEvents.save(new AuditEvent(
                user.getId(),
                "LOGIN_ROLE_MISMATCH",
                "USER",
                user.getId().toString(),
                ipAddress));

        throw new AuthException(
                "Select the correct login type: Customer or Administrator.");
    }

    return user;
}

    private boolean isExpectedOtp(User user, String otp, String purpose) {
        return user.getOtpHash() != null && purpose.equals(user.getOtpPurpose()) && user.getOtpExpiry() != null && user.getOtpExpiry().isAfter(LocalDateTime.now()) && passwordEncoder.matches(otp, user.getOtpHash());
    }
    private void validateNewPassword(String password, String confirmation) {
        if (!password.equals(confirmation)) throw new RegistrationException("New password and confirmation do not match.");
        if (!password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*") || !password.matches(".*\\d.*") || !password.matches(".*[^A-Za-z0-9].*"))
            throw new RegistrationException("Password needs uppercase, lowercase, number, and special character.");
    }
    private User getUser(String email) { return users.findByEmailIgnoreCase(email).orElseThrow(() -> new AuthException("No O Bank profile was found for this email.")); }
    private String email(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private String name(String value) { return value.trim().replaceAll("\\s+", " "); }
    private String visible(String value) { return showDemoCredentials ? value : null; }
}
