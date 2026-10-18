package com.inetbank.banking.internalpayment;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreditCardEmiDebitRequest(

        @NotNull(message = "User ID is required.")
        @Positive(message = "User ID must be positive.")
        Long userId,

        @NotNull(message = "Source account ID is required.")
        @Positive(message = "Source account ID must be positive.")
        Long sourceAccountId,

        @NotNull(message = "Amount is required.")
        @DecimalMin(value = "0.01")
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount,

        @NotBlank(message = "Reference number is required.")
        String referenceNumber
) {
}