package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MerchantPurchaseReversalResponse(

        Long merchantPurchaseId,
        String referenceNumber,
        String reversalReferenceNumber,
        BigDecimal cardChargeAmount,
        long pointsRedeemed,
        long pointsEarned,
        String status,
        LocalDateTime reversedAt,
        String message
) {
}