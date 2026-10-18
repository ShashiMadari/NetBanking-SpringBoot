package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateCreditCardEmiPlanRequest(

        @NotNull(message = "Credit card ID is required.")
        @Positive(message = "Credit card ID must be positive.")
        Long cardId,

        @NotNull(message = "Conversion amount is required.")
        @DecimalMin(
                value = "1000.00",
                message = "Minimum EMI conversion amount is INR 1000."
        )
        @Digits(
                integer = 12,
                fraction = 2,
                message = "Conversion amount can have up to 2 decimal places."
        )
        BigDecimal conversionAmount,

        @NotNull(message = "EMI tenure is required.")
        Integer tenureMonths
) {
}