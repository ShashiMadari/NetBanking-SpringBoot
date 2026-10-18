package com.inetbank.investment;

import java.math.BigDecimal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        contextId = "investmentBankingDebitClient",
        name = "o-bank-banking",
        path = "/internal/investment/accounts",
        configuration = BankingApiKeyFeignConfig.class
)
public interface BankingDebitClient {

    @PostMapping("/debit")
    AccountDebitResponse debit(
            @RequestBody AccountDebitRequest request
    );

    record AccountDebitRequest(
            Long userId,
            Long accountId,
            BigDecimal amount,
            String transactionType,
            String referenceNumber
    ) {
    }

    record AccountDebitResponse(
            Long transactionId,
            String referenceNumber,
            boolean alreadyProcessed
    ) {
    }
}