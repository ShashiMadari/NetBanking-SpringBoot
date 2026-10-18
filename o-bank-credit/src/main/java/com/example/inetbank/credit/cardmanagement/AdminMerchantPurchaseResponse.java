package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminMerchantPurchaseResponse(
        Long merchantPurchaseId,
        String referenceNumber,
        String status,

        Long customerUserId,
        String customerName,
        String customerEmail,

        Long cardId,
        String cardLastFour,

        Long merchantId,
        String merchantCode,
        String merchantName,
        String merchantCategory,

        BigDecimal purchaseAmount,
        long pointsRedeemed,
        BigDecimal pointsDiscountAmount,
        BigDecimal cardChargeAmount,
        long pointsEarned,

        LocalDateTime purchasedAt,

        String reversalReferenceNumber,
        String reversalReason,
        LocalDateTime reversedAt
) {
}