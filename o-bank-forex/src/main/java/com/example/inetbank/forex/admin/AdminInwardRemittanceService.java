package com.example.inetbank.forex.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;
import com.example.inetbank.forex.event.ForexKafkaEventPublisher;
import com.example.inetbank.forex.event.ForexKafkaEventPublisher.ForexIdentityEvent;

// KAFKA CHANGE: Direct Identity REST client is no longer used here.
// import com.example.inetbank.forex.integration.IdentitySystemNotificationClient;

@Service
public class AdminInwardRemittanceService {

    private final AdminInwardRemittanceRepository repository;

    // KAFKA CHANGE
    private final ForexKafkaEventPublisher kafkaEvents;

    // private final IdentitySystemNotificationClient identityNotifications;

    public AdminInwardRemittanceService(
            AdminInwardRemittanceRepository repository,

            // KAFKA CHANGE
            ForexKafkaEventPublisher kafkaEvents

            // IdentitySystemNotificationClient identityNotifications
    ) {
        this.repository = repository;
        this.kafkaEvents = kafkaEvents;

        // this.identityNotifications = identityNotifications;
    }

    @Transactional
    public ForexTransactionResponse simulate(
            Long adminUserId,
            InwardRemittanceRequest request) {

        String currencyCode = request.currencyCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        if ("INR".equals(currencyCode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inward Forex remittance must use a foreign currency."
            );
        }

        BigDecimal rate = repository.findActiveRate(currencyCode);

        if (rate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The selected currency is unavailable."
            );
        }

        AdminInwardRemittanceRepository.Wallet wallet =
                repository.getOrCreateWalletForUpdate(
                        request.userId(),
                        currencyCode
                );

        repository.creditWallet(
                wallet.walletId(),
                request.amount()
        );

        String referenceNumber = "FXI-" + UUID.randomUUID()
                .toString()
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);

        BigDecimal inrEquivalent = request.amount()
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);

        Long transactionId = repository.createTransaction(
                request.userId(),
                currencyCode,
                request.amount(),
                inrEquivalent,
                referenceNumber,
                request.senderReference() == null
                        ? null
                        : "INWARD-" + request.senderReference().trim()
        );

        repository.createWalletCreditLedger(
                transactionId,
                wallet.walletId(),
                currencyCode,
                request.amount(),
                wallet.availableBalance()
                        .add(request.amount())
        );

        repository.createRemittance(
                transactionId,
                request.purpose().trim(),
                request.senderReference()
        );

        String message = "Forex inward remittance of "
                + request.amount().toPlainString()
                + " "
                + currencyCode
                + " was credited to your Forex wallet. "
                + "Reference: "
                + referenceNumber;

        /*
         * KAFKA CHANGE:
         *
         * Old direct REST approach:
         *
         * identityNotifications.createNotification(
         *         request.userId(),
         *         "PAYMENT",
         *         message
         * );
         *
         * identityNotifications.createAuditEvent(
         *         adminUserId,
         *         "FOREX_INWARD_REMITTANCE_COMPLETED",
         *         "FOREX_TRANSACTION",
         *         String.valueOf(transactionId)
         * );
         *
         * New Kafka event:
         * notificationUserId = customer receiving the Forex money.
         * auditUserId        = admin who initiated the inward remittance.
         */
        ForexIdentityEvent event = new ForexIdentityEvent(
                request.userId(),
                adminUserId,
                "PAYMENT",
                message,
                "FOREX_INWARD_REMITTANCE_COMPLETED",
                "FOREX_TRANSACTION",
                String.valueOf(transactionId),
                LocalDateTime.now()
        );

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        kafkaEvents.publish(
                                event,
                                referenceNumber
                        );
                    }
                }
        );

        return repository.findTransaction(
                transactionId
        );
    }
}