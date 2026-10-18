package com.inetbank.identity.registration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/internal/emails")
public class InternalOtpEmailController {

    private final EmailService emailService;
    private final UserRepository users;
    private final String internalApiKey;

    public InternalOtpEmailController(
            EmailService emailService,
            UserRepository users,
            @Value("${app.internal.api-key}")
            String internalApiKey) {

        this.emailService = emailService;
        this.users = users;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/otp")
    public ResponseEntity<Void> sendOtp(
            @RequestHeader(
                    value = "X-Internal-Api-Key",
                    defaultValue = ""
            )
            String suppliedApiKey,
            @Valid @RequestBody OtpEmailRequest request) {

        if (!MessageDigest.isEqual(
                internalApiKey.getBytes(StandardCharsets.UTF_8),
                suppliedApiKey.getBytes(StandardCharsets.UTF_8))) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid internal API key."
            );
        }

        User customer = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer was not found."
                ));

        if (!"CUSTOMER".equals(customer.getRole())
                || !customer.isBankCustomerActive()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Customer is unavailable or inactive."
            );
        }

        emailService.sendOtp(
                customer.getEmail(),
                request.otp(),
                request.purpose()
        );

        return ResponseEntity.noContent().build();
    }

    public record OtpEmailRequest(
            @NotBlank
            @Email
            @Size(max = 120)
            String email,

            @NotBlank
            @Pattern(regexp = "[0-9]{6}")
            String otp,

            @NotBlank
            @Size(max = 100)
            String purpose
    ) {
    }
}