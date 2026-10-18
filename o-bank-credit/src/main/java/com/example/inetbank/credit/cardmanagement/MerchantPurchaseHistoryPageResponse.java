package com.example.inetbank.credit.cardmanagement;

import java.util.List;

public record MerchantPurchaseHistoryPageResponse(
        Long cardId,
        int page,
        int pageSize,
        long totalPurchases,
        int totalPages,
        List<MerchantPurchaseHistoryResponse> purchases
) {
}