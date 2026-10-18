package com.example.inetbank.forex.card;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ForexCardSpendRequest(

        @NotBlank
        @Size(max = 120)
        String merchantName,

        @NotNull
        @DecimalMin("0.000001")
        BigDecimal amount
) {
}