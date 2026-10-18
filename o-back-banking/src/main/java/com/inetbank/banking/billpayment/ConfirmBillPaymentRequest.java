package com.inetbank.banking.billpayment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmBillPaymentRequest(

        @NotBlank
        @Pattern(
                regexp = "^[0-9]{6}$",
                message = "must be a 6-digit OTP")
        String otp
) {
}