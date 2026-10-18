package com.example.inetbank.credit.cardmanagement;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.inetbank.credit.integration.IdentityCustomerEmailClient;
import com.example.inetbank.credit.integration.IdentityScheduledNotificationClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;
@Component
public class MonthlyCreditCardStatementScheduler {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    MonthlyCreditCardStatementScheduler.class
            );

    private static final ZoneId INDIA =
            ZoneId.of("Asia/Kolkata");

    private final CreditCardStatementDeliveryRepository deliveries;
    private final CreditCardMonthlyStatementService statementService;
    private final CreditCardMonthlyStatementEmailService emailService;
    private final IdentityCustomerEmailClient identityCustomerEmailClient;
    private final IdentitySystemNotificationClient audits;
    private final IdentityScheduledNotificationClient notifications;
    public MonthlyCreditCardStatementScheduler(
            CreditCardStatementDeliveryRepository deliveries,
            CreditCardMonthlyStatementService statementService,
            CreditCardMonthlyStatementEmailService emailService,
            IdentityCustomerEmailClient identityCustomerEmailClient,
            IdentitySystemNotificationClient audits,
            IdentityScheduledNotificationClient notifications) {

        this.deliveries = deliveries;
        this.statementService = statementService;
        this.emailService = emailService;
        this.identityCustomerEmailClient =
                identityCustomerEmailClient;
        this.audits = audits;
        this.notifications = notifications;
    }

    @Scheduled(
            cron = "${credit-card-statement-scheduler.cron:0 0 9 1 * *}",
            zone = "Asia/Kolkata"
    )
    public void sendPreviousMonthStatements() {

        YearMonth statementMonth = YearMonth.now(INDIA)
                .minusMonths(1);

        List<CreditCardStatementDeliveryRepository
                .StatementDeliveryCandidate> cards =
                deliveries.findUndeliveredCards(
                        statementMonth.getYear(),
                        statementMonth.getMonthValue()
                );

        LOG.info(
                "Starting monthly credit-card statement delivery. "
                        + "year={}, month={}, cards={}",
                statementMonth.getYear(),
                statementMonth.getMonthValue(),
                cards.size()
        );

        for (CreditCardStatementDeliveryRepository
                .StatementDeliveryCandidate card : cards) {

            sendStatement(card, statementMonth);
        }
    }

    private void sendStatement(
            CreditCardStatementDeliveryRepository
                    .StatementDeliveryCandidate card,
            YearMonth statementMonth) {

        try {
            String customerEmail =
                    identityCustomerEmailClient.getCustomerEmail(
                            card.userId()
                    );

            CreditCardMonthlyStatementResponse statement =
                    statementService.getStatement(
                            card.userId(),
                            card.cardId(),
                            statementMonth.getYear(),
                            statementMonth.getMonthValue()
                    );

            boolean sent = emailService.send(
                    customerEmail,
                    statement
            );

            if (sent) {
                deliveries.recordEmailDelivery(
                        card.cardId(),
                        statementMonth.getYear(),
                        statementMonth.getMonthValue()
                );

                audits.createAuditEvent(
                        card.userId(),
                        "CREDIT_CARD_STATEMENT_EMAIL_SENT",
                        "CREDIT_CARD",
                        String.valueOf(card.cardId())
                );

                LOG.info(
                        "Monthly credit-card statement delivered. "
                                + "cardId={}, year={}, month={}",
                        card.cardId(),
                        statementMonth.getYear(),
                        statementMonth.getMonthValue()
                );

                notifications.createNotification(
                        card.userId(),
                        "PAYMENT",
                        "Your O Bank credit-card statement for "
                                + statementMonth
                                + " was sent to your registered email as a PDF attachment."
                );
            }
        } catch (RuntimeException exception) {
            LOG.warn(
                    "Monthly credit-card statement delivery failed. "
                            + "cardId={}, year={}, month={}",
                    card.cardId(),
                    statementMonth.getYear(),
                    statementMonth.getMonthValue(),
                    exception
            );
        }
    }
}