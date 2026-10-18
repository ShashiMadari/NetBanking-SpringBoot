package com.example.inetbank.forex.integration;

import java.math.BigDecimal;

public record BankingForexConversionDebitRequest(

        Long userId,

        Long sourceAccountId,

        BigDecimal amount,

        String referenceNumber
) {
}