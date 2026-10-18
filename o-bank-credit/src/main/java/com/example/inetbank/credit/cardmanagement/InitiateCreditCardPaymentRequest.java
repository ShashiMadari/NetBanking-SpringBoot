package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record InitiateCreditCardPaymentRequest(

        @NotNull(message = "Source account ID is required.")
        Long sourceAccountId,

        @NotNull(message = "Card ID is required.")
        Long cardId,

        @NotNull(message = "Payment amount is required.")
        @DecimalMin(
                value = "1.00",
                message = "Payment amount must be greater than zero."
        )
        @Digits(
                integer = 13,
                fraction = 2,
                message = "Amount must have at most 13 digits and 2 decimal places."
        )
        BigDecimal amount
) {
}