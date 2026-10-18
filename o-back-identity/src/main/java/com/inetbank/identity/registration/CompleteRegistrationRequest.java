package com.inetbank.identity.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteRegistrationRequest(
    @NotBlank @Email @Size(max = 120) String email,
    @NotBlank @Size(min = 8, max = 72) String demoPassword,
    @NotBlank @Size(min = 8, max = 72) String newPassword,
    @NotBlank String confirmPassword
) {}
