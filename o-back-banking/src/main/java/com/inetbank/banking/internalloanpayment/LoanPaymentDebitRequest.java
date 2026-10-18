package com.inetbank.banking.internalloanpayment;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoanPaymentDebitRequest(

        @NotNull
        Long userId,

        @NotNull
        Long sourceAccountId,

        @NotBlank
        @Pattern(regexp = "^(EMI|PART_PAYMENT|FORECLOSURE)$")
        String paymentType,

        @NotNull
        @DecimalMin(value = "1.00")
        @Digits(integer = 13, fraction = 2)
        BigDecimal amount,

        @NotBlank
        @Size(max = 40)
        String referenceNumber) {
}