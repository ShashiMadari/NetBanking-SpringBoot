package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreditCardEmiPaymentOtpResponse(
        Long paymentOrderId,
        Long emiPlanId,
        String referenceNumber,
        BigDecimal amount,
        String status,
        LocalDateTime otpExpiresAt,
        String message
) {
}