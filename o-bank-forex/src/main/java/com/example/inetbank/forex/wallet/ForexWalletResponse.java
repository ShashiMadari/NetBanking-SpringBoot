package com.example.inetbank.forex.wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ForexWalletResponse(

        Long walletId,

        String currencyCode,

        BigDecimal availableBalance,

        LocalDateTime updatedAt
) {
}