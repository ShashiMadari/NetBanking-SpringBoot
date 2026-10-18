package com.inetbank.investment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class InvestmentNotificationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    InvestmentNotificationService.class
            );

    private final IdentityNotificationClient client;

    public InvestmentNotificationService(
            IdentityNotificationClient client) {

        this.client = client;
    }

    public void afterCommit(
            Long userId,
            String eventType,
            String message) {

        if (userId == null
                || eventType == null
                || message == null) {

            throw new IllegalArgumentException(
                    "Notification recipient, type and message are required."
            );
        }

        String safeMessage = message.length() <= 500
                ? message
                : message.substring(0, 497) + "...";

        IdentityNotificationClient.NotificationRequest request =
                new IdentityNotificationClient.NotificationRequest(
                        userId,
                        eventType,
                        safeMessage
                );

        if (TransactionSynchronizationManager
                    .isActualTransactionActive()
                && TransactionSynchronizationManager
                    .isSynchronizationActive()) {

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {

                        @Override
                        public void afterCommit() {
                            deliver(request);
                        }
                    }
            );

        } else {
            deliver(request);
        }
    }

    private void deliver(
            IdentityNotificationClient.NotificationRequest request) {

        try {
            client.create(request);

        } catch (RuntimeException exception) {

            // Notification failure must not undo a completed payment.
            // This implementation logs failures but has no durable retries.
            LOG.warn(
                    "Investment notification delivery failed. "
                            + "userId={}, type={}, exceptionType={}",
                    request.userId(),
                    request.eventType(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}