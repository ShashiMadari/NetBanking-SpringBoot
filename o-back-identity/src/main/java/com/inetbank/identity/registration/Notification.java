package com.inetbank.identity.registration;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "notification_id") private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 15) private String channel;
    @Column(name = "event_type", nullable = false, length = 30) private String eventType;
    @Column(nullable = false, length = 500) private String message;
    @Column(name = "is_read", nullable = false) private Character isRead = 'N';
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    protected Notification() {}
    Notification(Long userId, String message) { this.userId=userId; this.channel="IN_APP"; this.eventType="SECURITY"; this.message=message; this.createdAt=LocalDateTime.now(); }
}
