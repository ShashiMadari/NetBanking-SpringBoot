package com.example.inetbank.credit.cardmanagement;

public record RewardMerchantResponse(
        Long merchantId,
        String merchantCode,
        String merchantName,
        String merchantCategory,
        boolean rewardEligible,
        String status
) {
}