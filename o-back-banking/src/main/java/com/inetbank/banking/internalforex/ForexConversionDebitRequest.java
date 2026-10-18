package com.inetbank.banking.internalforex;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ForexConversionDebitRequest(

        @NotNull
        Long userId,

        @NotNull
        Long sourceAccountId,

        @NotNull
        @DecimalMin("1.00")
        @Digits(integer = 13, fraction = 2)
        BigDecimal amount,

        @NotBlank
        @Size(max = 40)
        String referenceNumber
) {
}