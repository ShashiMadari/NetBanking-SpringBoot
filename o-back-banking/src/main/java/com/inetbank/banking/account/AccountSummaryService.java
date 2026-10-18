package com.inetbank.banking.account;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountSummaryService {

    private final AccountSummaryRepository accounts;

    public AccountSummaryService(AccountSummaryRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<AccountSummaryResponse> getForCustomer(Long userId) {
        return accounts.findForCustomerId(userId);
    }

    @Transactional(readOnly = true)
    public AccountSummaryResponse getForCustomerAccount(
            Long userId,
            Long accountId) {

        return accounts.findForCustomerIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account was not found."
                ));
    }
}