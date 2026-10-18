package com.example.inetbank.forex.admin;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InwardRemittanceRequest(

        @NotNull
        Long userId,

        @NotBlank
        @Pattern(regexp = "[A-Za-z]{3}")
        String currencyCode,

        @NotNull
        @DecimalMin("0.000001")
        BigDecimal amount,

        @NotBlank
        @Size(max = 200)
        String purpose,

        @Size(max = 80)
        String senderReference
) {
}