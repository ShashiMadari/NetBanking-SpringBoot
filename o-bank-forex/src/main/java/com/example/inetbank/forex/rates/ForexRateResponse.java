package com.example.inetbank.forex.rates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ForexRateResponse(

        String currencyCode,

        BigDecimal currentRateInr,

        Integer markupBps,

        String status,

        String referenceSource,

        LocalDate referenceAsOf,

        LocalDateTime updatedAt
) {
}