package com.example.inetbank.credit.integration;

public record IdentityScheduledNotificationRequest(
        Long userId,
        String eventType,
        String message
) {
}