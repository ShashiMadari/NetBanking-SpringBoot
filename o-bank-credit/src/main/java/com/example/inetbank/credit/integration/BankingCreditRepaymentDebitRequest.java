package com.example.inetbank.credit.integration;

import java.math.BigDecimal;

public record BankingCreditRepaymentDebitRequest(
        Long userId,
        Long sourceAccountId,
        BigDecimal amount,
        String referenceNumber
) {
}