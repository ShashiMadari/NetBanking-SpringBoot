package com.example.inetbank.credit.cardmanagement;

import java.util.List;

public record CreditCardRewardLedgerPageResponse(
        Long cardId,
        int page,
        int pageSize,
        long totalEntries,
        int totalPages,
        long availablePoints,
        List<CreditCardRewardLedgerResponse> entries
) {
}