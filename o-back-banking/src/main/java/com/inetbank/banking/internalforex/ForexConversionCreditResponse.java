package com.inetbank.banking.internalforex;

import java.math.BigDecimal;

public record ForexConversionCreditResponse(

        Long transactionId,
        String referenceNumber,
        BigDecimal amount,
        boolean alreadyProcessed,
        String message
) {
}