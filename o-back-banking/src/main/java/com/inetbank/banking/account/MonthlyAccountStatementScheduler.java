package com.inetbank.banking.account;

import java.time.YearMonth;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.inetbank.banking.integration.IdentitySystemNotificationClient;

@Component
public class MonthlyAccountStatementScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(
            MonthlyAccountStatementScheduler.class
    );

    private final AccountStatementDeliveryRepository deliveries;
    private final MonthlyAccountStatementService statements;
    private final IdentitySystemNotificationClient identity;

    public MonthlyAccountStatementScheduler(
            AccountStatementDeliveryRepository deliveries,
            MonthlyAccountStatementService statements,
            IdentitySystemNotificationClient identity) {

        this.deliveries = deliveries;
        this.statements = statements;
        this.identity = identity;
    }

    @Scheduled(
            cron = "${account-statement-scheduler.cron:0 0 9 1 * *}",
            zone = "Asia/Kolkata"
    )
    public void sendPreviousMonthStatements() {

        YearMonth statementMonth = YearMonth.now()
                .minusMonths(1);

        List<AccountStatementDeliveryRepository.ActiveAccount>
                accounts = deliveries.findActiveCustomerAccounts();

        LOG.info(
                "Starting monthly account statement delivery. "
                        + "year={}, month={}, accounts={}",
                statementMonth.getYear(),
                statementMonth.getMonthValue(),
                accounts.size()
        );

        for (AccountStatementDeliveryRepository.ActiveAccount account
                : accounts) {

            deliver(account, statementMonth);
        }
    }

    private void deliver(
            AccountStatementDeliveryRepository.ActiveAccount account,
            YearMonth statementMonth) {

        if (deliveries.alreadyDelivered(
                account.accountId(),
                statementMonth)) {

            return;
        }

        try {
            statements.emailPdf(
                    account.customerUserId(),
                    account.accountId(),
                    statementMonth
            );

            deliveries.recordEmailDelivery(
                    account.accountId(),
                    statementMonth
            );

            safeNotification(
                    account.customerUserId(),
                    "STATEMENT",
                    "Your monthly account statement for "
                            + statementMonth
                            + " was sent to your email. "
                            + "Account: "
                            + account.accountNumber()
            );

            safeAudit(
                    account.customerUserId(),
                    "ACCOUNT_STATEMENT_EMAIL_SENT",
                    "ACCOUNT",
                    account.accountId()
            );

            LOG.info(
                    "Monthly account statement delivered. "
                            + "accountId={}, month={}",
                    account.accountId(),
                    statementMonth
            );
        } catch (RuntimeException exception) {
            LOG.error(
                    "Monthly account statement delivery failed. "
                            + "accountId={}, month={}",
                    account.accountId(),
                    statementMonth,
                    exception
            );
        }
    }

    private void safeNotification(
            Long customerUserId,
            String eventType,
            String message) {

        try {
            identity.createSystemNotification(
                    customerUserId,
                    eventType,
                    message
            );
        } catch (RuntimeException exception) {
            LOG.error(
                    "Account-statement notification failed. userId={}",
                    customerUserId,
                    exception
            );
        }
    }

    private void safeAudit(
            Long customerUserId,
            String action,
            String entityType,
            Long entityId) {

        try {
            identity.createAuditEvent(
                    customerUserId,
                    action,
                    entityType,
                    String.valueOf(entityId)
            );
        } catch (RuntimeException exception) {
            LOG.error(
                    "Account-statement audit event failed. accountId={}",
                    entityId,
                    exception
            );
        }
    }
}