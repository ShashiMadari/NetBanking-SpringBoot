package com.example.inetbank.forex.conversion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ForexConversionRequest(

        @NotBlank
        String quoteId,

        @NotNull
        Long sourceAccountId,

        @NotBlank
        String paymentMethod,

        @Size(max = 64)
        String idempotencyKey
) {
}