package com.inetbank.identity.audit;

import java.time.LocalDateTime;

public record AuditEventResponse(
        Long auditEventId,
        Long userId,
        String action,
        String entityType,
        String entityId,
        String ipAddress,
        LocalDateTime createdAt) {
}