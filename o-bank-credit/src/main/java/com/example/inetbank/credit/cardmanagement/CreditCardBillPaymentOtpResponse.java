package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreditCardBillPaymentOtpResponse(
        Long paymentOrderId,
        String referenceNumber,
        BigDecimal amount,
        String status,
        LocalDateTime otpExpiresAt,
        String message
) {
}