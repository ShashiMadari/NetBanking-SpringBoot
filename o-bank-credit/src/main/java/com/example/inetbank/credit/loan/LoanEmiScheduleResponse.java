package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LoanEmiScheduleResponse(
        Long scheduleId,
        int installmentNumber,
        LocalDate dueDate,
        BigDecimal openingPrincipal,
        BigDecimal emiAmount,
        BigDecimal principalComponent,
        BigDecimal interestComponent,
        BigDecimal closingPrincipal,
        String status,
        LocalDateTime paidAt) {
}