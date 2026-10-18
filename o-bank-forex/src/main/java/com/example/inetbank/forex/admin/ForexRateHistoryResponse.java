package com.example.inetbank.forex.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ForexRateHistoryResponse(

        Long rateHistoryId,

        String currencyCode,

        BigDecimal previousRateInr,

        BigDecimal newRateInr,

        String changeReason,

        String referenceSource,

        LocalDateTime createdAt
) {
}