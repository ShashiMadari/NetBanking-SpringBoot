package com.inetbank.banking.fundtransfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmTransferRequest(

        @NotBlank
        @Pattern(
                regexp = "^[0-9]{6}$",
                message = "must be a 6-digit OTP")
        String otp
) {
}