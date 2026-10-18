package com.example.inetbank.credit.cardmanagement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RewardMerchantStatusRequest(

        @NotBlank(message = "Merchant status is required.")
        @Pattern(
                regexp = "ACTIVE|INACTIVE",
                message = "Status must be ACTIVE or INACTIVE."
        )
        String status
) {
}