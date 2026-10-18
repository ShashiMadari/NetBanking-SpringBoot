package com.inetbank.identity.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank @Email @Size(max = 120) String email,
    @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be a 6-digit OTP") String otp,
    @NotBlank @Size(min = 8, max = 72) String newPassword,
    @NotBlank String confirmPassword
) {}
