package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoanPaymentConfirmationResponse(
        Long paymentOrderId,
        Long loanId,
        String paymentType,
        BigDecimal amount,
        String referenceNumber,
        String status,
        LocalDateTime completedAt,
        String message) {
}