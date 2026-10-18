package com.inetbank.investment.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;

public record BankingDebitSucceededEvent(
        String eventId,
        String originalEventId,
        Long orderId,
        Long userId,
        Long accountId,
        BigDecimal amount,
        Long bankingTransactionId,
        String referenceNumber,
        Instant completedAt
) {
}