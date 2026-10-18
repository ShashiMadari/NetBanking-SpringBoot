package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InitiateCreditCardBillPaymentRequest(

        @NotNull(message = "Credit card ID is required.")
        @Positive(message = "Credit card ID must be positive.")
        Long cardId,

        @NotNull(message = "Biller ID is required.")
        @Positive(message = "Biller ID must be positive.")
        Long billerId,

        @NotBlank(message = "Consumer reference is required.")
        String consumerReference,

        @NotNull(message = "Payment amount is required.")
        @DecimalMin(
                value = "1.00",
                message = "Payment amount must be at least INR 1.00."
        )
        @Digits(
                integer = 12,
                fraction = 2,
                message = "Payment amount can have up to 2 decimal places."
        )
        BigDecimal amount
) {
}