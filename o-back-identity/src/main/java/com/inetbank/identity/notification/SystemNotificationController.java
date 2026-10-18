package com.inetbank.identity.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/internal/system")
public class SystemNotificationController {

    private final UserRepository users;
    private final NotificationService notificationService;
    private final NotificationEmailService notificationEmailService;
    private final JdbcTemplate jdbcTemplate;
    private final String internalApiKey;

    public SystemNotificationController(
            UserRepository users,
            NotificationService notificationService,
            NotificationEmailService notificationEmailService,
            JdbcTemplate jdbcTemplate,
            @Value("${app.internal.api-key}") String internalApiKey) {

        this.users = users;
        this.notificationService = notificationService;
        this.notificationEmailService = notificationEmailService;
        this.jdbcTemplate = jdbcTemplate;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/payment-otp-expired")
    @Transactional
    public ResponseEntity<Void> paymentOtpExpired(
            @RequestHeader("X-Internal-Api-Key") String requestApiKey,
            @Valid @RequestBody SystemPaymentOtpExpiredRequest request) {

        verifyInternalApiKey(requestApiKey);

        User customer = users.findById(request.customerUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer was not found."
                ));

        String message = "Your payment OTP expired before confirmation. "
                + "The payment was not completed. Reference: "
                + request.referenceNumber();

        notificationService.create(
                customer.getId(),
                "PAYMENT",
                message
        );

        notificationEmailService.sendAdminNotificationEmail(
                customer.getEmail(),
                "PAYMENT",
                message
        );

        jdbcTemplate.update("""
                INSERT INTO audit_events (
                    user_id,
                    action,
                    entity_type,
                    entity_id,
                    ip_address,
                    created_at
                )
                VALUES (
                    ?,
                    'PAYMENT_OTP_EXPIRED',
                    'PAYMENT_ORDER',
                    ?,
                    'BANKING-SERVICE',
                    SYSTIMESTAMP
                )
                """,
                customer.getId(),
                String.valueOf(request.paymentOrderId())
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/audit-events")
    @Transactional
    public ResponseEntity<Void> createAuditEvent(
            @RequestHeader("X-Internal-Api-Key") String requestApiKey,
            @Valid @RequestBody SystemAuditEventRequest request) {

        verifyInternalApiKey(requestApiKey);

        if (!users.existsById(request.userId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Audit-event user was not found."
            );
        }

        jdbcTemplate.update("""
                INSERT INTO audit_events (
                    user_id,
                    action,
                    entity_type,
                    entity_id,
                    ip_address,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, SYSTIMESTAMP)
                """,
                request.userId(),
                request.action().trim(),
                request.entityType().trim(),
                request.entityId().trim(),
                request.ipAddress() == null
                        ? "BANKING-SERVICE"
                        : request.ipAddress().trim()
        );

        return ResponseEntity.noContent().build();
    }

    private void verifyInternalApiKey(String requestApiKey) {
        boolean valid = requestApiKey != null
                && MessageDigest.isEqual(
                        requestApiKey.getBytes(StandardCharsets.UTF_8),
                        internalApiKey.getBytes(StandardCharsets.UTF_8)
                );

        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid internal service key."
            );
        }
    }
}