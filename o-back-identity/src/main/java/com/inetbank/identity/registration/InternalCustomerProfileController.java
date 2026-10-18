package com.inetbank.identity.registration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/customers")
public class InternalCustomerProfileController {

    private final UserRepository users;
    private final String internalApiKey;

    public InternalCustomerProfileController(
            UserRepository users,
            @Value("${app.internal.api-key}") String internalApiKey) {

        this.users = users;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/by-email")
    public CustomerProfileResponse findByEmail(
            @RequestParam("email") String email,
            @RequestHeader(
                    value = "X-Internal-Api-Key",
                    required = false
            ) String suppliedApiKey) {

        validateInternalApiKey(suppliedApiKey);

        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer was not found."
                ));

        return new CustomerProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus()
        );
    }

    private void validateInternalApiKey(String suppliedApiKey) {
        if (suppliedApiKey == null
                || !MessageDigest.isEqual(
                        internalApiKey.getBytes(StandardCharsets.UTF_8),
                        suppliedApiKey.getBytes(StandardCharsets.UTF_8))) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid internal API key."
            );
        }
    }

    public record CustomerProfileResponse(
            Long userId,
            String fullName,
            String email,
            String role,
            String status
    ) {
    }
}