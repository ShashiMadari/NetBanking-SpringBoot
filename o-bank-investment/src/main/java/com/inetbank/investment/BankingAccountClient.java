package com.inetbank.investment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        contextId = "investmentBankingAccountClient",
        name = "o-bank-banking",
        path = "/internal/accounts",
        configuration = BankingApiKeyFeignConfig.class
)
public interface BankingAccountClient {

    @GetMapping("/{accountId}/users/{userId}/active")
    ActiveAccountResponse hasActiveOwnedAccount(
            @PathVariable("accountId") Long accountId,
            @PathVariable("userId") Long userId
    );

    record ActiveAccountResponse(boolean active) {
    }
}