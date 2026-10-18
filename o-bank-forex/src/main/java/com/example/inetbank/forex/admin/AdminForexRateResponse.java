package com.example.inetbank.forex.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminForexRateResponse(

        String currencyCode,

        BigDecimal baseRateInr,

        BigDecimal currentRateInr,

        Integer markupBps,

        String status,

        String referenceSource,

        LocalDate referenceAsOf,

        LocalDateTime updatedAt
) {
}