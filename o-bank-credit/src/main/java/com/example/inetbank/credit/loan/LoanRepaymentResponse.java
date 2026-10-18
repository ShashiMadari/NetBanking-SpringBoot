package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoanRepaymentResponse(
        Long repaymentId,
        Long paymentOrderId,
        Long sourceAccountId,
        String repaymentType,
        BigDecimal amount,
        BigDecimal outstandingBefore,
        BigDecimal outstandingAfter,
        BigDecimal principalComponent,
        BigDecimal interestComponent,
        String referenceNumber,
        LocalDateTime paymentDate) {
}