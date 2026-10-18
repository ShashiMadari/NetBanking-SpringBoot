package com.example.inetbank.credit.cardmanagement;

public record CreditCardRewardBalanceResponse(
        Long cardId,
        String cardLastFour,
        long availablePoints,
        String pointValueRule
) {
}