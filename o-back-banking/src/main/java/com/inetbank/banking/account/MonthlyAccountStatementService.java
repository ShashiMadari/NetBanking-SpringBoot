package com.inetbank.banking.account;

import java.time.YearMonth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.inetbank.banking.integration.IdentityCustomerContactClient;
import com.inetbank.banking.integration.IdentityCustomerContactResponse;

@Service
public class MonthlyAccountStatementService {

    private final AccountSummaryRepository accounts;
    private final AccountStatementRepository transactions;
    private final IdentityCustomerContactClient customerContacts;
    private final MonthlyAccountStatementPdfService pdfService;
    private final MonthlyAccountStatementEmailService emailService;

    public MonthlyAccountStatementService(
            AccountSummaryRepository accounts,
            AccountStatementRepository transactions,
            IdentityCustomerContactClient customerContacts,
            MonthlyAccountStatementPdfService pdfService,
            MonthlyAccountStatementEmailService emailService) {

        this.accounts = accounts;
        this.transactions = transactions;
        this.customerContacts = customerContacts;
        this.pdfService = pdfService;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public MonthlyAccountStatementData getStatement(
            Long customerUserId,
            Long accountId,
            YearMonth statementMonth) {

        AccountSummaryResponse account = accounts
                .findForCustomerIdAndAccountId(
                        customerUserId,
                        accountId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account was not found for this customer."
                ));

        IdentityCustomerContactResponse customer =
                customerContacts.getCustomerContact(customerUserId);

        return new MonthlyAccountStatementData(
                customer.fullName(),
                account.accountId(),
                account.accountNumber(),
                account.accountType(),
                account.branchName(),
                account.ifscCode(),
                account.currency(),
                account.status(),
                statementMonth,
                account.availableBalance(),
                account.bookBalance(),
                transactions.findMonthlyForCustomerAccount(
                        accountId,
                        customerUserId,
                        statementMonth
                )
        );
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(
            Long customerUserId,
            Long accountId,
            YearMonth statementMonth) {

        return pdfService.create(getStatement(
                customerUserId,
                accountId,
                statementMonth
        ));
    }

    @Transactional(readOnly = true)
    public void emailPdf(
            Long customerUserId,
            Long accountId,
            YearMonth statementMonth) {

        MonthlyAccountStatementData statement = getStatement(
                customerUserId,
                accountId,
                statementMonth
        );

        IdentityCustomerContactResponse customer =
                customerContacts.getCustomerContact(customerUserId);

        if (!emailService.send(customer.email(), statement)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Account statement email could not be sent. "
                            + "Please try again later."
            );
        }
    }
}