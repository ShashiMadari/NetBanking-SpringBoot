package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanOverviewResponse(
        Long loanId,
        String loanAccountNumber,
        String loanType,
        BigDecimal loanAmount,
        BigDecimal outstandingAmount,
        BigDecimal interestRate,
        int tenureMonths,
        int installmentsPaid,
        BigDecimal emiAmount,
        LocalDate nextDueDate,
        String status) {
}