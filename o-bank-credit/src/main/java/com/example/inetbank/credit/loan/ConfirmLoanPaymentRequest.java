package com.example.inetbank.credit.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmLoanPaymentRequest(

        @NotBlank(message = "OTP is required.")
        @Pattern(
                regexp = "^\\d{6}$",
                message = "OTP must contain exactly 6 digits."
        )
        String otp) {
}