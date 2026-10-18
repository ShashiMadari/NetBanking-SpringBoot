package com.example.inetbank.credit.cardmanagement;

import java.time.LocalDateTime;

public record CardPinChangeOtpResponse(
        Long pinChangeOrderId,
        Long cardId,
        String status,
        LocalDateTime otpExpiresAt,
        String message
) {
}