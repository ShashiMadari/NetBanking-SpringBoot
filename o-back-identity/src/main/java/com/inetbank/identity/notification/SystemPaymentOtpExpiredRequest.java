package com.inetbank.identity.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SystemPaymentOtpExpiredRequest(

        @NotNull
        Long customerUserId,

        @NotNull
        Long paymentOrderId,

        @NotBlank
        String referenceNumber
) {
}