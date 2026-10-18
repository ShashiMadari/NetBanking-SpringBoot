package com.inetbank.investment;

import java.math.BigDecimal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        contextId = "investmentBankingCreditClient",
        name = "o-bank-banking",
        path = "/internal/investment/accounts",
        configuration = BankingApiKeyFeignConfig.class
)
public interface BankingCreditClient {

    @PostMapping("/credit")
    AccountCreditResponse credit(@RequestBody AccountCreditRequest request);

    record AccountCreditRequest(
            Long userId,
            Long accountId,
            BigDecimal amount,
            String transactionType,
            String referenceNumber
    ) {
    }

    // credited=true also means an identical earlier request already succeeded.
    // Banking performs reference-based idempotency; no second credit occurs.
    record AccountCreditResponse(
            Long transactionId,
            boolean credited,
            String referenceNumber
    ) {
    }
}
