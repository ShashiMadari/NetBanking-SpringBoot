package com.inetbank.banking.customerpayment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerPaymentResponse(
        Long paymentOrderId,
        String referenceNumber,
        String paymentType,
        BigDecimal amount,
        String status,

        Long sourceAccountId,
        String sourceAccountNumber,

        Long beneficiaryId,
        String beneficiaryName,
        String beneficiaryAccountNumber,

        Long billerId,
        String billerName,
        String consumerReference,

        LocalDateTime requestedAt,
        LocalDateTime completedAt,

        String reversalReferenceNumber,
        String reversalReason,
        LocalDateTime reversedAt
) {
}