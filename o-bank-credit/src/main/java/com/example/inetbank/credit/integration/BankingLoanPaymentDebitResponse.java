package com.example.inetbank.credit.integration;

import java.math.BigDecimal;

public record BankingLoanPaymentDebitResponse(
        Long transactionId,
        String referenceNumber,
        BigDecimal amount,
        boolean alreadyProcessed,
        String message) {
}