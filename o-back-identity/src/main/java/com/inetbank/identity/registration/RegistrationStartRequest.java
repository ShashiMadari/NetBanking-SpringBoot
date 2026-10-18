package com.inetbank.identity.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationStartRequest(
    @NotBlank @Size(max = 120) String fullName,
    @NotBlank @Email @Size(max = 120) String email
) {}
