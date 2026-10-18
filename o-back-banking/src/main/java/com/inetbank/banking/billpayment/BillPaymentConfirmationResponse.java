package com.inetbank.banking.billpayment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillPaymentConfirmationResponse(
        Long paymentOrderId,
        String referenceNumber,
        BigDecimal amount,
        String status,
        LocalDateTime completedAt,
        String message
) {
}