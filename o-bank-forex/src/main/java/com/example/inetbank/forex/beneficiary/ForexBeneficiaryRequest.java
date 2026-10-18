package com.example.inetbank.forex.beneficiary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ForexBeneficiaryRequest(

        @NotBlank
        String beneficiaryName,

        @NotBlank
        @Pattern(regexp = "[A-Za-z]{2}")
        String countryCode,

        @NotBlank
        @Pattern(regexp = "[A-Za-z]{3}")
        String currencyCode,

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9]{8}([A-Za-z0-9]{3})?")
        String swiftBic,

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9-]{6,34}")
        String accountNumber
) {
}