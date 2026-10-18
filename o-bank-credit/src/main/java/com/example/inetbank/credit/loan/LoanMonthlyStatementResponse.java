package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LoanMonthlyStatementResponse(
        Long loanId,
        String loanAccountNumber,
        String loanType,
        String loanStatus,
        LocalDate statementFrom,
        LocalDate statementTo,
        BigDecimal originalLoanAmount,
        BigDecimal currentOutstandingAmount,
        BigDecimal annualInterestRate,
        BigDecimal emiAmount,
        LocalDate nextDueDate,
        BigDecimal totalEmiPayments,
        BigDecimal totalPartPayments,
        BigDecimal totalForeclosures,
        List<LoanMonthlyStatementEntryResponse> entries) {
}