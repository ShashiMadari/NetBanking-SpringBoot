package com.inetbank.investment.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;

public record InvestmentOrderPlacedEvent(
        String eventId,
        Long orderId,
        Long userId,
        Long accountId,
        BigDecimal amount,
        String referenceNumber,
        String transactionType,
        Instant createdAt
) {
}