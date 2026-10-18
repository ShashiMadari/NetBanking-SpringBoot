package com.inetbank.banking.internalpayment;

import java.math.BigDecimal;

public record CreditCardEmiDebitResponse(
        Long transactionId,
        String referenceNumber,
        BigDecimal amount,
        boolean alreadyProcessed,
        String message
) {
}