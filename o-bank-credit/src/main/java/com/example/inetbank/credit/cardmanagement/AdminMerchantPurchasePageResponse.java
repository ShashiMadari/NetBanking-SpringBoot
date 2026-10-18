package com.example.inetbank.credit.cardmanagement;

import java.util.List;

public record AdminMerchantPurchasePageResponse(
        String status,
        Long merchantId,
        int page,
        int pageSize,
        long totalPurchases,
        int totalPages,
        List<AdminMerchantPurchaseResponse> purchases
) {
}