package com.example.inetbank.forex.card;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ForexCardIssueRequest(

        @NotBlank
        @Pattern(regexp = "[A-Za-z]{3}")
        String currencyCode,

        @NotNull
        @DecimalMin("1.00")
        BigDecimal spendLimit
) {
}