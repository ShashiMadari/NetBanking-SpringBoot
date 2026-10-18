package com.inetbank.banking.account;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("hasRole('CUSTOMER')")
public class AccountSummaryController {

    private final AccountSummaryService accountSummaryService;

    public AccountSummaryController(
            AccountSummaryService accountSummaryService) {

        this.accountSummaryService = accountSummaryService;
    }

    @GetMapping("/summary")
    public List<AccountSummaryResponse> summary(
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        return accountSummaryService.getForCustomer(userId);
    }

    @GetMapping("/{accountId}")
    public AccountSummaryResponse account(
            @PathVariable Long accountId,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        return accountSummaryService.getForCustomerAccount(
                userId,
                accountId
        );
    }
}