package com.inetbank.identity.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInternalNotificationRequest(

        @NotBlank
        @Size(max = 30)
        String eventType,

        @NotBlank
        @Size(max = 500)
        String message
) {
}