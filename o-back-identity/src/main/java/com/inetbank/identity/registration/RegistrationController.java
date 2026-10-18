package com.inetbank.identity.registration;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

    private final RegistrationService registrationService;

    RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    ResponseEntity<RegistrationStartResponse> register(
            @Valid @RequestBody RegistrationStartRequest request,
            HttpServletRequest requestInfo) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.startRegistration(request, ip(requestInfo)));
    }

    @PostMapping("/complete-registration")
    MessageResponse completeRegistration(
            @Valid @RequestBody CompleteRegistrationRequest request,
            HttpServletRequest requestInfo) {

        return registrationService.completeRegistration(request, ip(requestInfo));
    }

    @PostMapping("/login/send-otp")
    LoginOtpResponse sendLoginOtp(
            @Valid @RequestBody SendLoginOtpRequest request,
            HttpServletRequest requestInfo) {

        return registrationService.sendLoginOtp(request, ip(requestInfo));
    }

    @PostMapping("/login/resend-otp")
    LoginOtpResponse resendLoginOtp(
            @Valid @RequestBody SendLoginOtpRequest request,
            HttpServletRequest requestInfo) {

        return registrationService.resendLoginOtp(request, ip(requestInfo));
    }

    @PostMapping("/login")
    AuthTokenResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest requestInfo) {

        return registrationService.login(request, ip(requestInfo));
    }

    @PostMapping("/forgot-password/send-otp")
    LoginOtpResponse sendResetOtp(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest requestInfo) {

        return registrationService.sendResetOtp(request, ip(requestInfo));
    }

    @PostMapping("/forgot-password/reset")
    MessageResponse resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest requestInfo) {

        return registrationService.resetPassword(request, ip(requestInfo));
    }

    @ExceptionHandler(RegistrationException.class)
    ResponseEntity<Map<String, String>> registrationError(
            RegistrationException e) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<Map<String, String>> authError(AuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", e.getMessage()));
    }

    private String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}