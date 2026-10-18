package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreditCardResponse(
        Long cardId,
        Long accountId,
        String lastFour,
        String cardType,
        String network,
        BigDecimal dailyLimit,
        BigDecimal creditLimit,
        BigDecimal outstandingAmount,
        BigDecimal minimumDueAmount,
        LocalDate paymentDueDate,
        String status,
        LocalDateTime createdAt
) {
}