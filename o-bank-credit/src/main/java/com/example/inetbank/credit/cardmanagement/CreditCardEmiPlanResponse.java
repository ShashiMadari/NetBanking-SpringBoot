package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditCardEmiPlanResponse(
        Long emiPlanId,
        Long cardId,
        BigDecimal conversionAmount,
        BigDecimal annualInterestRate,
        Integer tenureMonths,
        BigDecimal emiAmount,
        BigDecimal totalPayable,
        BigDecimal remainingPrincipal,
        LocalDate nextDueDate,
        String status
) {
}