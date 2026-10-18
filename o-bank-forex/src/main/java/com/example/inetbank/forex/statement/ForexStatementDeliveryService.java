package com.example.inetbank.forex.statement;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.example.inetbank.forex.event.ForexKafkaEventPublisher;
import com.example.inetbank.forex.event.ForexKafkaEventPublisher.ForexIdentityEvent;
import com.example.inetbank.forex.integration.IdentityCustomerEmailClient;

// KAFKA CHANGE: Direct Identity REST client is no longer used here.
// import com.example.inetbank.forex.integration.IdentitySystemNotificationClient;

@Service
public class ForexStatementDeliveryService {

    private final ForexStatementService statementService;
    private final ForexStatementEmailService emailService;
    private final IdentityCustomerEmailClient customerEmails;

    // KAFKA CHANGE
    private final ForexKafkaEventPublisher kafkaEvents;

    // private final IdentitySystemNotificationClient identityNotifications;

    public ForexStatementDeliveryService(
            ForexStatementService statementService,
            ForexStatementEmailService emailService,
            IdentityCustomerEmailClient customerEmails,

            // KAFKA CHANGE
            ForexKafkaEventPublisher kafkaEvents

            // IdentitySystemNotificationClient identityNotifications
    ) {
        this.statementService = statementService;
        this.emailService = emailService;
        this.customerEmails = customerEmails;
        this.kafkaEvents = kafkaEvents;

        // this.identityNotifications = identityNotifications;
    }

    public ForexStatementEmailResponse send(
            Long userId,
            ForexStatementEmailRequest request) {

        ForexTransactionStatementResponse statement =
                statementService.getStatement(
                        userId,
                        request.period(),
                        request.from(),
                        request.to(),
                        0,
                        100
                );

        String customerEmail = customerEmails.getEmail(
                userId
        );

        emailService.send(
                customerEmail,
                statement
        );

        String message = "Your Forex transaction statement for "
                + statement.statementFrom()
                + " to "
                + statement.statementTo()
                + " was sent to your registered email.";

        /*
         * KAFKA CHANGE:
         *
         * Old direct REST approach:
         *
         * identityNotifications.createNotification(
         *         userId,
         *         "STATEMENT",
         *         message
         * );
         *
         * identityNotifications.createAuditEvent(
         *         userId,
         *         "FOREX_STATEMENT_EMAIL_SENT",
         *         "FOREX_STATEMENT",
         *         String.valueOf(userId)
         * );
         *
         * New approach:
         * The email is sent first. Then Forex publishes a Kafka event.
         * Identity consumes it and creates the notification + audit row.
         */
        ForexIdentityEvent event = new ForexIdentityEvent(
                userId,
                userId,
                "STATEMENT",
                message,
                "FOREX_STATEMENT_EMAIL_SENT",
                "FOREX_STATEMENT",
                String.valueOf(userId),
                LocalDateTime.now()
        );

        String messageKey = "FOREX-STATEMENT-"
                + userId
                + "-"
                + statement.statementTo();

        kafkaEvents.publish(
                event,
                messageKey
        );

        return new ForexStatementEmailResponse(
                "Your Forex statement PDF was sent to your "
                        + "registered email.",
                statement.totalTransactions(),
                statement.statementFrom(),
                statement.statementTo()
        );
    }
}