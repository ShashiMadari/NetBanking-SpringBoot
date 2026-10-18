package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record UpdateCardLimitRequest(

        @NotNull(message = "Daily limit is required.")
        @DecimalMin(value = "1.00", message = "Daily limit must be greater than zero.")
        @Digits(integer = 10, fraction = 2,
                message = "Daily limit must have at most 10 digits and 2 decimal places.")
        BigDecimal dailyLimit
) {
}