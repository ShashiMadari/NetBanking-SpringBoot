package com.inetbank.banking.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountStatusChangeRequest(

        @NotBlank(message = "Reason is required.")
        @Size(
                min = 5,
                max = 500,
                message = "Reason must contain 5 to 500 characters."
        )
        String reason
) {
}