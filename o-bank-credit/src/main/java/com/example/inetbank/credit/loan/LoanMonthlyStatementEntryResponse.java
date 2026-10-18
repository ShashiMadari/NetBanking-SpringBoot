package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoanMonthlyStatementEntryResponse(
        LocalDateTime paymentDate,
        String repaymentType,
        String referenceNumber,
        BigDecimal amount,
        BigDecimal principalComponent,
        BigDecimal interestComponent,
        BigDecimal outstandingBefore,
        BigDecimal outstandingAfter) {
}