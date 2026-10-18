package com.example.inetbank.credit.integration;

import java.math.BigDecimal;

public record BankingLoanPaymentDebitRequest(
        Long userId,
        Long sourceAccountId,
        String paymentType,
        BigDecimal amount,
        String referenceNumber) {
}