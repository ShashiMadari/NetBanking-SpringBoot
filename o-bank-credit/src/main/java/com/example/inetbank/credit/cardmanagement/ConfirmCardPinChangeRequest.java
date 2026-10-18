package com.example.inetbank.credit.cardmanagement;

import jakarta.validation.constraints.Pattern;

public record ConfirmCardPinChangeRequest(

        @Pattern(
                regexp = "\\d{6}",
                message = "OTP must contain exactly six digits."
        )
        String otp
) {
}