package com.example.inetbank.forex.card;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ForexCardResponse(

        Long forexCardId,

        Long walletId,

        String cardNumberMasked,

        String cardToken,

        String status,

        BigDecimal spendLimit,

        BigDecimal availableBalance,

        LocalDateTime issuedAt
) {
}