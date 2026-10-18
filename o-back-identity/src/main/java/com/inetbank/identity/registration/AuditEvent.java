package com.inetbank.identity.registration;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity @Table(name = "audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "audit_event_id") private Long id;
    @Column(name = "user_id") private Long userId;
    @Column(nullable = false, length = 60) private String action;
    @Column(name = "entity_type", length = 50) private String entityType;
    @Column(name = "entity_id", length = 60) private String entityId;
    @Column(name = "ip_address", length = 45) private String ipAddress;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    protected AuditEvent() {}
    AuditEvent(Long userId, String action, String entityType, String entityId, String ipAddress) {
        this.userId=userId; this.action=action; this.entityType=entityType; this.entityId=entityId; this.ipAddress=ipAddress; this.createdAt=LocalDateTime.now();
    }
}
