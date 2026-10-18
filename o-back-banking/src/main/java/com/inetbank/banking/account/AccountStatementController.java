package com.inetbank.banking.account;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("hasRole('CUSTOMER')")
public class AccountStatementController {

    private final AccountStatementService accountStatementService;

    public AccountStatementController(
            AccountStatementService accountStatementService) {

        this.accountStatementService = accountStatementService;
    }

    @GetMapping("/{accountId}/transactions")
    public List<AccountStatementResponse> transactions(
            @PathVariable Long accountId,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        return accountStatementService.getForCustomerAccount(
                accountId,
                userId
        );
    }

    @GetMapping("/{accountId}/transactions/statement")
    public AccountTransactionStatementResponse statement(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "RECENT")
            StatementPeriod period,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        return accountStatementService.getStatementForCustomerAccount(
                accountId,
                userId,
                period,
                from,
                to,
                page,
                size
        );
    }
}