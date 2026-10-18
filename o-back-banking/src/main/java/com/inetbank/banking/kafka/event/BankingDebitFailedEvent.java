package com.inetbank.banking.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;

public record BankingDebitFailedEvent(
        String eventId,
        String originalEventId,
        Long orderId,
        Long userId,
        Long accountId,
        BigDecimal amount,
        String failureCode,
        String failureReason,
        Instant completedAt
) {
}