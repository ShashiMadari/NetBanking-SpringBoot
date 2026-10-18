package com.example.inetbank.credit.cardmanagement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRewardMerchantRequest(

        @NotBlank(message = "Merchant code is required.")
        @Size(
                max = 30,
                message = "Merchant code must not exceed 30 characters."
        )
        @Pattern(
                regexp = "^[A-Za-z0-9-]+$",
                message = "Merchant code may contain letters, numbers, and hyphens only."
        )
        String merchantCode,

        @NotBlank(message = "Merchant name is required.")
        @Size(
                max = 120,
                message = "Merchant name must not exceed 120 characters."
        )
        String merchantName,

        @NotBlank(message = "Merchant category is required.")
        @Size(
                max = 50,
                message = "Merchant category must not exceed 50 characters."
        )
        String merchantCategory,

        boolean rewardEligible
) {
}