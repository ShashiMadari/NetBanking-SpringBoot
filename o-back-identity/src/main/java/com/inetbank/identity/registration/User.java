package com.inetbank.identity.registration;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity @Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "user_id")
    private Long id;
    @Column(name = "full_name", nullable = false, length = 120) private String fullName;
    @Column(nullable = false, unique = true, length = 120) private String email;
    @Column(name = "phone", unique = true, length = 15) private String mobile;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(nullable = false, length = 15) private String role;
    @Column(name = "otp_hash", length = 255) private String otpHash;
    @Column(name = "otp_expiry") private LocalDateTime otpExpiry;
    @Column(name = "otp_purpose", length = 20) private String otpPurpose;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "locked_until") private LocalDateTime lockedUntil;
    @Column(name = "login_status", nullable = false, length = 20) private String loginStatus;
    @Column(name = "net_banking_status", nullable = false, length = 25) private String netBankingStatus;
    @Column(nullable = false, length = 15) private String status;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    protected User() {}
    User(String fullName, String email, String passwordHash) {
        this.fullName = fullName; this.email = email; this.passwordHash = passwordHash;
        this.role = "CUSTOMER"; this.status = "ACTIVE"; this.netBankingStatus = "NOT_REGISTERED"; this.failedAttempts = 0; this.loginStatus = "LOGGED_OUT"; this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getMobile() {
        return mobile;
    }

    public String getNetBankingStatus() {
        return netBankingStatus;
    }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public String getOtpHash() { return otpHash; }
    public LocalDateTime getOtpExpiry() { return otpExpiry; }
    public String getOtpPurpose() { return otpPurpose; }
    public int getFailedAttempts() { return failedAttempts; }
    public boolean isBankCustomerActive() { return "ACTIVE".equals(status); }
    public boolean isNetBankingNotRegistered() { return "NOT_REGISTERED".equals(netBankingStatus); }
    public boolean isNetBankingPending() { return "PENDING_ACTIVATION".equals(netBankingStatus); }
    public boolean isNetBankingActive() { return "ACTIVE".equals(netBankingStatus); }
    public boolean isAuthenticationActive() { return "ADMIN".equals(role) ? isBankCustomerActive() : isBankCustomerActive() && isNetBankingActive(); }
    public void beginNetBankingRegistration(String hash) { passwordHash = hash; netBankingStatus = "PENDING_ACTIVATION"; failedAttempts = 0; lockedUntil = null; }
    public void activateNetBankingWithPassword(String hash) { passwordHash = hash; netBankingStatus = "ACTIVE"; failedAttempts = 0; lockedUntil = null; }
    public void setOtp(String hash, LocalDateTime expiry, String purpose) { otpHash = hash; otpExpiry = expiry; otpPurpose = purpose; }
    public void clearOtp() { otpHash = null; otpExpiry = null; otpPurpose = null; }
    public void recordFailedAttempt() { failedAttempts++; if (failedAttempts >= 5) { netBankingStatus = "LOCKED"; lockedUntil = LocalDateTime.now().plusMinutes(30); } }
    public void clearFailedAttempts() { failedAttempts = 0; lockedUntil = null; }
    public void markLoggedIn() { loginStatus = "LOGGED_IN"; }
    public boolean unlockIfLockExpired() {
        if ("LOCKED".equals(netBankingStatus) && lockedUntil != null && !lockedUntil.isAfter(LocalDateTime.now())) {
            netBankingStatus = "ACTIVE"; failedAttempts = 0; lockedUntil = null; return true;
        }
        return false;
    }
    public boolean isTemporarilyLocked() { return "LOCKED".equals(netBankingStatus) && (lockedUntil == null || lockedUntil.isAfter(LocalDateTime.now())); }
}
