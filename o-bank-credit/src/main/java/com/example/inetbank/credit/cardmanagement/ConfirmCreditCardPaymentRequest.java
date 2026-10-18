package com.example.inetbank.credit.cardmanagement;

import jakarta.validation.constraints.Pattern;

public record ConfirmCreditCardPaymentRequest(

        @Pattern(
                regexp = "\\d{6}",
                message = "OTP must contain exactly six digits."
        )
        String otp
) {
}