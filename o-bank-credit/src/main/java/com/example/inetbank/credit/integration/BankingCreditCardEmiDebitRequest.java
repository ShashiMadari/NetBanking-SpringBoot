package com.example.inetbank.credit.integration;

import java.math.BigDecimal;

public record BankingCreditCardEmiDebitRequest(
        Long userId,
        Long sourceAccountId,
        BigDecimal amount,
        String referenceNumber
) {
}