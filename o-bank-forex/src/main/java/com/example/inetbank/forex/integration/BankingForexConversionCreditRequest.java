package com.example.inetbank.forex.integration;

import java.math.BigDecimal;

public record BankingForexConversionCreditRequest(

        Long userId,
        Long destinationAccountId,
        BigDecimal amount,
        String referenceNumber
) {
}