package com.inetbank.banking.internalloanpayment;

import java.math.BigDecimal;

public record LoanPaymentDebitResponse(
        Long transactionId,
        String referenceNumber,
        BigDecimal amount,
        boolean alreadyProcessed,
        String message) {
}