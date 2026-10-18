package com.example.inetbank.forex.conversion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ForexTransactionResponse(

        Long forexTransactionId,

        String transactionType,

        String fromCurrency,

        String toCurrency,

        BigDecimal fromAmount,

        BigDecimal receiveAmount,

        BigDecimal customerRateInr,

        BigDecimal inrEquivalent,

        String paymentMethod,

        String status,

        String referenceNumber,

        LocalDateTime createdAt
) {
}