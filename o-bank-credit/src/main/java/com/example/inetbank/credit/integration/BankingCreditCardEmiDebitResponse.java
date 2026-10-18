package com.example.inetbank.credit.integration;

import java.math.BigDecimal;

public record BankingCreditCardEmiDebitResponse(
        Long transactionId,
        String referenceNumber,
        BigDecimal amount,
        boolean alreadyProcessed,
        String message
) {
}