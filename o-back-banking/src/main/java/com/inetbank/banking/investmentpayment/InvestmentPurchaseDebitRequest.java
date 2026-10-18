package com.inetbank.banking.investmentpayment;

import java.math.BigDecimal;

public record InvestmentPurchaseDebitRequest(
        Long userId,
        Long sourceAccountId,
        BigDecimal amount,
        String referenceNumber
) {
}