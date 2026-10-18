package com.inetbank.banking.billpayment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillPaymentOtpResponse(
        Long paymentOrderId,
        String referenceNumber,
        BigDecimal amount,
        String status,
        LocalDateTime otpExpiresAt,
        String message
) {
}