package com.inetbank.banking.internalpayment;

import java.math.BigDecimal;

public record CreditRepaymentDebitResponse(
        Long transactionId,
        String referenceNumber,
        BigDecimal amount,
        boolean alreadyProcessed,
        String message
) {
}