package com.example.inetbank.forex.quote;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ForexQuoteResponse(

        String quoteId,

        String fromCurrency,

        String toCurrency,

        BigDecimal fromAmount,

        BigDecimal receiveAmount,

        BigDecimal customerRateInr,

        Integer markupBps,

        OffsetDateTime expiresAt,

        String status
) {
}
