package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreditCardActivityResponse(
        String activityKey,
        String activityType,
        String direction,
        BigDecimal amount,
        String referenceNumber,
        String status,
        String description,
        LocalDateTime occurredAt
) {
}