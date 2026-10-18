package com.example.inetbank.credit.cardmanagement;

import java.time.LocalDateTime;

public record CreditCardRewardLedgerResponse(
        Long rewardLedgerId,
        Long merchantPurchaseId,
        String eventType,
        long points,
        String referenceNumber,
        String description,
        LocalDateTime createdAt
) {
}