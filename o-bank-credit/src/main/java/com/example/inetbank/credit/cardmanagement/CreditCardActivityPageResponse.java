package com.example.inetbank.credit.cardmanagement;

import java.util.List;

public record CreditCardActivityPageResponse(
        Long cardId,
        String cardLastFour,
        int page,
        int pageSize,
        long totalActivities,
        int totalPages,
        List<CreditCardActivityResponse> activities
) {
}