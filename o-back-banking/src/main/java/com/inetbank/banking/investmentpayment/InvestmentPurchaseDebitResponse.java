package com.inetbank.banking.investmentpayment;

import java.math.BigDecimal;

public record InvestmentPurchaseDebitResponse(
        Long transactionId,
        String referenceNumber,
        BigDecimal amount,
        boolean alreadyProcessed,
        String message
) {
}