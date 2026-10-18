package com.inetbank.banking.dashboard;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.inetbank.banking.account.AccountSummaryRepository;
import com.inetbank.banking.account.AccountSummaryResponse;
import com.inetbank.banking.integration.IdentityCustomerContactClient;
import com.inetbank.banking.integration.IdentityCustomerContactResponse;

@Service
public class DashboardService {

    private final AccountSummaryRepository accounts;
    private final IdentityCustomerContactClient customerContacts;

    public DashboardService(
            AccountSummaryRepository accounts,
            IdentityCustomerContactClient customerContacts) {

        this.accounts = accounts;
        this.customerContacts = customerContacts;
    }

    @Transactional(readOnly = true)
    public CustomerDashboardResponse getDashboard(
            Long customerUserId) {

        IdentityCustomerContactResponse customer =
                customerContacts.getCustomerContact(
                        customerUserId
                );

        List<DashboardAccountResponse> dashboardAccounts =
                accounts.findForCustomerId(customerUserId)
                        .stream()
                        .map(this::toDashboardAccount)
                        .toList();

        return new CustomerDashboardResponse(
                customerUserId,
                customer.fullName(),
                customer.email(),
                dashboardAccounts
        );
    }

    private DashboardAccountResponse toDashboardAccount(
            AccountSummaryResponse account) {

        return new DashboardAccountResponse(
                account.accountId(),
                account.accountNumber(),
                account.accountType(),
                account.availableBalance(),
                account.bookBalance(),
                account.lienAmount(),
                account.currency(),
                account.status()
        );
    }
}