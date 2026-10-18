package com.example.inetbank.forex.quote;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ForexQuoteRequest(

        @NotBlank
        String fromCurrency,

        @NotBlank
        String toCurrency,

        @NotNull
        @DecimalMin("0.000001")
        BigDecimal fromAmount
) {
}