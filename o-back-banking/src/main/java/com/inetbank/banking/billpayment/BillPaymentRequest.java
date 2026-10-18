package com.inetbank.banking.billpayment;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BillPaymentRequest(

        @NotNull
        Long sourceAccountId,

        @NotNull
        Long billerId,

        @NotBlank
        @Size(max = 100)
        String consumerReference,

        @NotNull
        @DecimalMin(value = "1.00", message = "must be at least 1.00")
        BigDecimal amount
) {
}