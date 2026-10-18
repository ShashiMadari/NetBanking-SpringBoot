package com.example.inetbank.forex.admin;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdminForexRateRequest(

        @NotNull
        @DecimalMin("0.000001")
        BigDecimal baseRateInr,

        @NotNull
        @Min(0)
        @Max(1000)
        Integer markupBps,

        @NotNull
        Boolean active
) {
}