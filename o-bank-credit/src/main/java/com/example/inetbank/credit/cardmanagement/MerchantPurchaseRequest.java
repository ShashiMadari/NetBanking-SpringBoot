package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record MerchantPurchaseRequest(

        @NotNull(message = "Card ID is required.")
        @Positive(message = "Card ID must be positive.")
        Long cardId,

        @NotNull(message = "Merchant ID is required.")
        @Positive(message = "Merchant ID must be positive.")
        Long merchantId,

        @NotNull(message = "Purchase amount is required.")
        @DecimalMin(
                value = "1.00",
                message = "Purchase amount must be at least INR 1.00."
        )
        @Digits(
                integer = 12,
                fraction = 2,
                message = "Purchase amount can have up to 2 decimal places."
        )
        BigDecimal purchaseAmount,

        @NotNull(message = "Points to redeem is required.")
        @DecimalMin(
                value = "0",
                message = "Points to redeem cannot be negative."
        )
        Long pointsToRedeem,

        @NotBlank(message = "Card PIN is required.")
        @Pattern(
                regexp = "\\d{4}",
                message = "Card PIN must contain exactly 4 digits."
        )
        String cardPin
) {
}