package com.example.inetbank.forex.remittance;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OutwardRemittanceRequest(

        @NotNull
        Long forexBeneficiaryId,

        @NotNull
        @DecimalMin("0.000001")
        BigDecimal amount,

        @NotBlank
        @Size(max = 200)
        String purpose,

        @NotBlank
        @Size(max = 64)
        String idempotencyKey
) {
}