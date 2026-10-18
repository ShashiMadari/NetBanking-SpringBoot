package com.example.inetbank.credit.loan;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record InitiateLoanPaymentRequest(

        @NotNull(message = "Source account is required.")
        Long sourceAccountId,

        @NotNull(message = "Loan is required.")
        Long loanId,

        @NotNull(message = "Payment type is required.")
        @Pattern(
                regexp = "^(EMI|PART_PAYMENT|FORECLOSURE)$",
                message = "Payment type must be EMI, PART_PAYMENT, or FORECLOSURE."
        )
        String paymentType,

        @NotNull(message = "Payment amount is required.")
        @DecimalMin(
                value = "0.01",
                message = "Payment amount must be greater than zero."
        )
        BigDecimal amount) {
}