package com.inetbank.banking.account;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountStatementResponse(
        Long transactionId,
        String transactionType,
        String entrySide,
        BigDecimal amount,
        String referenceNumber,
        String status,
        LocalDateTime transactionDate) {
}