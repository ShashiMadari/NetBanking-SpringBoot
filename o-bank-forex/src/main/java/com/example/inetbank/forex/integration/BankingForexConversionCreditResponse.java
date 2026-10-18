package com.example.inetbank.forex.integration;

import java.math.BigDecimal;

public record BankingForexConversionCreditResponse(

        Long transactionId,
        String referenceNumber,
        BigDecimal amount,
        boolean alreadyProcessed,
        String message
) {
}