package com.inetbank.identity.notification;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.inetbank.identity.registration.User;
import com.inetbank.identity.registration.UserRepository;

@Service
public class NotificationService {

    private final CustomerNotificationJdbcRepository notifications;
    private final UserRepository users;

    public NotificationService(
            CustomerNotificationJdbcRepository notifications,
            UserRepository users) {

        this.notifications = notifications;
        this.users = users;
    }

    @Transactional
    public void create(
            Long userId,
            String eventType,
            String message) {

        notifications.create(
                userId,
                clean(eventType),
                clean(message)
        );
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> recent(
            String email,
            NotificationPeriod period,
            NotificationCategory category,
            NotificationReadStatus readStatus,
            String search,
            int limit) {

        User customer = customer(email);

        int safeLimit = Math.max(1, Math.min(limit, 50));

        return notifications.find(
                customer.getId(),
                period,
                category,
                readStatus,
                search,
                safeLimit
        );
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse unreadCount(
            String email) {

        User customer = customer(email);

        return new UnreadNotificationCountResponse(
                notifications.unreadCount(customer.getId())
        );
    }

    @Transactional
    public NotificationActionResponse markRead(
            String email,
            Long notificationId) {

        User customer = customer(email);

        boolean updated = notifications.markRead(
                notificationId,
                customer.getId()
        );

        if (!updated) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Notification was not found."
            );
        }

        return new NotificationActionResponse(
                "Notification marked as read."
        );
    }

    @Transactional
    public NotificationActionResponse markAllRead(
            String email) {

        User customer = customer(email);

        int updated = notifications.markAllRead(customer.getId());

        return new NotificationActionResponse(
                updated + " notification(s) marked as read."
        );
    }

    private User customer(String email) {

        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated customer was not found."));

        if (!"CUSTOMER".equals(user.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only customers can access notifications.");
        }

        return user;
    }

    private String clean(String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Notification event type and message are required."
            );
        }

        return value.trim().replaceAll("\\s+", " ");
    }
}