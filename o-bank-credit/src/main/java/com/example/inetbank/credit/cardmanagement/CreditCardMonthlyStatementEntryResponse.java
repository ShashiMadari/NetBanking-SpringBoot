package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreditCardMonthlyStatementEntryResponse(
        String activityType,
        String description,
        String referenceNumber,
        BigDecimal amount,
        LocalDateTime occurredAt,
        String status
) {
}