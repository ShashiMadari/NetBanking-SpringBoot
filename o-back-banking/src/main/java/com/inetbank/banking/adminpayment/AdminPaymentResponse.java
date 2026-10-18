package com.inetbank.banking.adminpayment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPaymentResponse(
        Long paymentOrderId,
        String referenceNumber,
        String paymentType,
        BigDecimal amount,
        String status,

        Long sourceAccountId,
        String sourceAccountNumber,

        Long customerUserId,
        String customerName,
        String customerEmail,

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
        LocalDateTime reversedAt,
        Long reversedByUserId,

        boolean reversalEligible
) {
}