package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MerchantPurchaseHistoryResponse(
        Long merchantPurchaseId,
        Long cardId,
        String cardLastFour,
        Long merchantId,
        String merchantName,
        String merchantCategory,
        BigDecimal purchaseAmount,
        long pointsRedeemed,
        BigDecimal pointsDiscountAmount,
        BigDecimal cardChargeAmount,
        long pointsEarned,
        String referenceNumber,
        String status,
        LocalDateTime purchasedAt,
        String reversalReferenceNumber,
        String reversalReason,
        LocalDateTime reversedAt
) {
}