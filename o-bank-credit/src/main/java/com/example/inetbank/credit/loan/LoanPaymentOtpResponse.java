package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoanPaymentOtpResponse(
        Long paymentOrderId,
        Long loanId,
        String paymentType,
        BigDecimal amount,
        String referenceNumber,
        String status,
        LocalDateTime otpExpiresAt,
        String message) {
}