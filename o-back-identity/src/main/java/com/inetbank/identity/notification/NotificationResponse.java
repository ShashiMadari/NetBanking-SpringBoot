package com.inetbank.identity.notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        String channel,
        String eventType,
        String message,
        boolean read,
        LocalDateTime createdAt) {
}