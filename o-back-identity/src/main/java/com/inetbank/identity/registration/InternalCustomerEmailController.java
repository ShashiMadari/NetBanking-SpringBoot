package com.inetbank.identity.registration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/customers")
public class InternalCustomerEmailController {

    private final UserRepository users;
    private final String internalApiKey;

    public InternalCustomerEmailController(
            UserRepository users,
            @Value("${app.internal.api-key}") String internalApiKey) {

        this.users = users;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/{userId}/email")
    public CustomerEmailResponse customerEmail(
            @PathVariable Long userId,
            @RequestHeader(
                    value = "X-Internal-Api-Key",
                    required = false
            ) String suppliedApiKey) {

        validateInternalApiKey(suppliedApiKey);

        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer was not found."
                ));

        return new CustomerEmailResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName()
        );
    }

    private void validateInternalApiKey(String suppliedApiKey) {
        if (suppliedApiKey == null
                || !MessageDigest.isEqual(
                        internalApiKey.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        suppliedApiKey.getBytes(
                                StandardCharsets.UTF_8
                        ))) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid internal API key."
            );
        }
    }

    public record CustomerEmailResponse(
            Long userId,
            String email,
            String fullName
    ) {
    }
}