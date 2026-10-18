package com.example.inetbank.forex.beneficiary;

import java.time.LocalDateTime;

public record ForexBeneficiaryResponse(

        Long forexBeneficiaryId,

        String beneficiaryName,

        String countryCode,

        String currencyCode,

        String swiftBic,

        String maskedAccountNumber,

        String status,

        LocalDateTime createdAt
) {
}