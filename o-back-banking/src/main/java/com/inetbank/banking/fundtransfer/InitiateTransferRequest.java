package com.inetbank.banking.fundtransfer;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record InitiateTransferRequest(

        @NotNull
        Long sourceAccountId,

        @NotNull
        Long beneficiaryId,

        @NotNull
        @DecimalMin(value = "1.00", message = "must be at least 1.00")
        BigDecimal amount
) {
}