package com.example.inetbank.credit.cardmanagement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantPurchaseReversalRequest(

        @NotBlank(message = "Reversal reason is required.")
        @Size(
                min = 5,
                max = 500,
                message = "Reason must contain 5 to 500 characters."
        )
        String reason
) {
}