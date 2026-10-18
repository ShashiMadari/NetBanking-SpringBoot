package com.inetbank.banking.adminpayment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentReversalResponse(

        Long paymentOrderId,
        String referenceNumber,
        String reversalReferenceNumber,
        BigDecimal amount,
        String status,
        LocalDateTime reversedAt,
        String message
) {
}