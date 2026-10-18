package com.inetbank.banking.beneficiary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddBeneficiaryRequest(

        @NotBlank
        @Size(max = 150)
        String beneficiaryName,

        @NotBlank
        @Pattern(
                regexp = "^[0-9]{9,34}$",
                message = "must contain 9 to 34 digits")
        String beneficiaryAccountNumber,

        @NotBlank
        @Pattern(
                regexp = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$",
                message = "must be a valid IFSC code")
        String ifscCode,

        @Size(max = 150)
        String bankName
) {
}