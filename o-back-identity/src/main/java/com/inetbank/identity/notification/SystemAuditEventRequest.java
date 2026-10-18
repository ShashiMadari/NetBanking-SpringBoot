package com.inetbank.identity.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SystemAuditEventRequest(

        @NotNull
        Long userId,

        @NotBlank
        @Size(max = 60)
        String action,

        @NotBlank
        @Size(max = 50)
        String entityType,

        @NotBlank
        @Size(max = 60)
        String entityId,

        @Size(max = 45)
        String ipAddress
) {
}