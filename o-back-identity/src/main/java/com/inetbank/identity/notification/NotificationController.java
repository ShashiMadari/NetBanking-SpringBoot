package com.inetbank.identity.notification;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasRole('CUSTOMER')")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(
            NotificationService notificationService) {

        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> recent(
            @RequestParam(defaultValue = "ALL")
            NotificationPeriod period,

            @RequestParam(defaultValue = "ALL")
            NotificationCategory category,

            @RequestParam(defaultValue = "ALL")
            NotificationReadStatus readStatus,

            @RequestParam(required = false)
            String search,

            @RequestParam(defaultValue = "20")
            int limit,

            Authentication authentication) {

        return notificationService.recent(
                authentication.getName(),
                period,
                category,
                readStatus,
                search,
                limit
        );
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse unreadCount(
            Authentication authentication) {

        return notificationService.unreadCount(
                authentication.getName()
        );
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationActionResponse markRead(
            @PathVariable Long notificationId,
            Authentication authentication) {

        return notificationService.markRead(
                authentication.getName(),
                notificationId
        );
    }

    @PatchMapping("/read-all")
    public NotificationActionResponse markAllRead(
            Authentication authentication) {

        return notificationService.markAllRead(
                authentication.getName()
        );
    }
}