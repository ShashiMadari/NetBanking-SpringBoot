package com.inetbank.identity.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.inetbank.identity.registration.User;
import com.inetbank.identity.registration.UserRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

    private final NotificationService notificationService;
    private final UserRepository users;
    private final NotificationEmailService notificationEmailService;
    private final String internalApiKey;

    public InternalNotificationController(
            NotificationService notificationService,
            UserRepository users,
            NotificationEmailService notificationEmailService,
            @Value("${app.internal.api-key}") String internalApiKey) {

        this.notificationService = notificationService;
        this.users = users;
        this.notificationEmailService = notificationEmailService;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> create(
            @Valid @RequestBody CreateInternalNotificationRequest request,
            Authentication authentication) {

        User user = users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated customer was not found."
                ));

        notificationService.create(
                user.getId(),
                request.eventType(),
                request.message()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> createForCustomer(
            @Valid @RequestBody CreateAdminNotificationRequest request) {

        User user = users.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer was not found."
                ));

        notificationService.create(
                user.getId(),
                request.eventType(),
                request.message()
        );

        notificationEmailService.sendAdminNotificationEmail(
                user.getEmail(),
                request.eventType(),
                request.message()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/system")
    public ResponseEntity<Void> createForSystem(
            @RequestHeader(
                    value = "X-Internal-Api-Key",
                    required = false
            ) String suppliedApiKey,
            @Valid @RequestBody CreateAdminNotificationRequest request) {

        validateInternalApiKey(suppliedApiKey);

        User user = users.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer was not found."
                ));

        notificationService.create(
                user.getId(),
                request.eventType(),
                request.message()
        );

        return ResponseEntity.noContent().build();
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
}