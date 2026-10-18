package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;

public record CardActionResponse(
        Long cardId,
        String lastFour,
        String cardType,
        String status,
        BigDecimal dailyLimit,
        String message
) {
}