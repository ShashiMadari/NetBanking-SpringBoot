package com.example.inetbank.forex.card;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ForexCardAmountRequest(

        @NotNull
        @DecimalMin("0.000001")
        BigDecimal amount
) {
}