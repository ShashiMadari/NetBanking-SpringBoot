package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreditCardEmiScheduleResponse(
        Long scheduleId,
        Integer installmentNumber,
        LocalDate dueDate,
        BigDecimal openingPrincipal,
        BigDecimal emiAmount,
        BigDecimal principalComponent,
        BigDecimal interestComponent,
        BigDecimal closingPrincipal,
        String status,
        LocalDateTime paidAt
) {
}