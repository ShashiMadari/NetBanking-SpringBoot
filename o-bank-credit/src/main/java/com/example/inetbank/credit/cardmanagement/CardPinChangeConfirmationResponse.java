package com.example.inetbank.credit.cardmanagement;

import java.time.LocalDateTime;

public record CardPinChangeConfirmationResponse(
        Long pinChangeOrderId,
        Long cardId,
        String status,
        LocalDateTime completedAt,
        String message
) {
}