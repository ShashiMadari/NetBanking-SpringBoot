package com.example.inetbank.credit.loan;

import java.time.YearMonth;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.inetbank.credit.integration.IdentityCustomerEmailClient;
import com.example.inetbank.credit.integration.IdentityCustomerEmailResponse;
import com.example.inetbank.credit.integration.IdentityScheduledNotificationClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;

@Component
public class MonthlyLoanStatementScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(
            MonthlyLoanStatementScheduler.class
    );

    private final LoanStatementDeliveryRepository deliveries;
    private final LoanMonthlyStatementService statements;
    private final IdentityCustomerEmailClient customerEmailClient;
    private final IdentityScheduledNotificationClient notifications;
    private final IdentitySystemNotificationClient audits;

    public MonthlyLoanStatementScheduler(
            LoanStatementDeliveryRepository deliveries,
            LoanMonthlyStatementService statements,
            IdentityCustomerEmailClient customerEmailClient,
            IdentityScheduledNotificationClient notifications,
            IdentitySystemNotificationClient audits) {

        this.deliveries = deliveries;
        this.statements = statements;
        this.customerEmailClient = customerEmailClient;
        this.notifications = notifications;
        this.audits = audits;
    }

    @Scheduled(
            cron = "${loan-statement-scheduler.cron:0 10 9 1 * *}",
            zone = "Asia/Kolkata"
    )
    public void sendPreviousMonthStatements() {

        YearMonth statementMonth = YearMonth.now()
                .minusMonths(1);

        List<LoanStatementDeliveryRepository.ActiveLoan> loans =
                deliveries.findActiveLoans();

        LOG.info(
                "Starting monthly loan statement delivery. "
                        + "year={}, month={}, loans={}",
                statementMonth.getYear(),
                statementMonth.getMonthValue(),
                loans.size()
        );

        for (LoanStatementDeliveryRepository.ActiveLoan loan : loans) {
            deliver(loan, statementMonth);
        }
    }

    private void deliver(
            LoanStatementDeliveryRepository.ActiveLoan loan,
            YearMonth statementMonth) {

        if (deliveries.alreadyDelivered(
                loan.loanId(),
                statementMonth)) {

            return;
        }

        try {
            IdentityCustomerEmailResponse customer =
                    customerEmailClient.getCustomerContact(
                            loan.customerUserId()
                    );

            statements.emailPdf(
                    loan.customerUserId(),
                    customer.email(),
                    loan.loanId(),
                    statementMonth
            );

            deliveries.recordEmailDelivery(
                    loan.loanId(),
                    statementMonth
            );

            safeNotification(
                    loan.customerUserId(),
                    "LOAN",
                    "Your monthly loan statement for "
                            + statementMonth
                            + " was sent to your email."
            );

            safeAudit(
                    loan.customerUserId(),
                    "LOAN_STATEMENT_EMAIL_SENT",
                    "LOAN",
                    loan.loanId()
            );

            LOG.info(
                    "Monthly loan statement delivered. "
                            + "loanId={}, month={}",
                    loan.loanId(),
                    statementMonth
            );
        } catch (RuntimeException exception) {
            LOG.error(
                    "Monthly loan statement delivery failed. "
                            + "loanId={}, month={}",
                    loan.loanId(),
                    statementMonth,
                    exception
            );
        }
    }

    private void safeNotification(
            Long userId,
            String eventType,
            String message) {

        try {
            notifications.createNotification(
                    userId,
                    eventType,
                    message
            );
        } catch (RuntimeException exception) {
            LOG.error(
                    "Loan statement notification failed. userId={}",
                    userId,
                    exception
            );
        }
    }

    private void safeAudit(
            Long userId,
            String action,
            String entityType,
            Long entityId) {

        try {
            audits.createAuditEvent(
                    userId,
                    action,
                    entityType,
                    String.valueOf(entityId)
            );
        } catch (RuntimeException exception) {
            LOG.error(
                    "Loan statement audit event failed. loanId={}",
                    entityId,
                    exception
            );
        }
    }
}