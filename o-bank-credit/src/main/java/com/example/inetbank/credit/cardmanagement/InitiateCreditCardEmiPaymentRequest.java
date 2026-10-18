package com.example.inetbank.credit.cardmanagement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InitiateCreditCardEmiPaymentRequest(

        @NotNull(message = "Source account ID is required.")
        @Positive(message = "Source account ID must be positive.")
        Long sourceAccountId,

        @NotNull(message = "EMI plan ID is required.")
        @Positive(message = "EMI plan ID must be positive.")
        Long emiPlanId
) {
}