package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MerchantPurchaseResponse(
        Long merchantPurchaseId,
        String referenceNumber,
        BigDecimal purchaseAmount,
        long pointsRedeemed,
        BigDecimal pointsDiscountAmount,
        BigDecimal cardChargeAmount,
        long pointsEarned,
        String status,
        LocalDateTime purchasedAt,
        String message
) {
}