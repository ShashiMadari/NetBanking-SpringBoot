package com.example.inetbank.forex.card;

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
public class ForexCardLoadService {

    private final ForexCardLoadRepository repository;

    // KAFKA CHANGE
    private final ForexKafkaEventPublisher kafkaEvents;

    // private final IdentitySystemNotificationClient identityNotifications;

    public ForexCardLoadService(
            ForexCardLoadRepository repository,

            // KAFKA CHANGE
            ForexKafkaEventPublisher kafkaEvents

            // IdentitySystemNotificationClient identityNotifications
    ) {
        this.repository = repository;
        this.kafkaEvents = kafkaEvents;

        // this.identityNotifications = identityNotifications;
    }

    @Transactional
    public ForexTransactionResponse load(
            Long userId,
            Long cardId,
            ForexCardAmountRequest request) {

        ForexCardLoadRepository.Card card =
                repository.findCardForUpdate(userId, cardId);

        if (card == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Forex card was not found."
            );
        }

        if (!"ACTIVE".equals(card.status())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The Forex card is blocked or closed."
            );
        }

        ForexCardLoadRepository.Wallet wallet =
                repository.findWalletForUpdate(card.walletId());

        if (!repository.debitWallet(
                wallet.walletId(),
                request.amount())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient Forex wallet balance to load this card."
            );
        }

        BigDecimal rate = repository.findActiveRate(
                card.currencyCode()
        );

        if (rate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Forex card currency is unavailable."
            );
        }

        repository.creditCard(
                card.cardId(),
                request.amount()
        );

        String referenceNumber = "FXCL-" + UUID.randomUUID()
                .toString()
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);

        BigDecimal inrEquivalent = request.amount()
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);

        Long transactionId = repository.createTransaction(
                userId,
                card.currencyCode(),
                request.amount(),
                inrEquivalent,
                referenceNumber
        );

        repository.createWalletDebitLedger(
                transactionId,
                wallet.walletId(),
                card.currencyCode(),
                request.amount(),
                wallet.availableBalance()
                        .subtract(request.amount())
        );

        String message = "Forex card " + card.cardId()
                + " was loaded with "
                + request.amount().toPlainString()
                + " "
                + card.currencyCode()
                + ". Reference: "
                + referenceNumber;

        /*
         * KAFKA CHANGE:
         *
         * Old direct REST approach:
         *
         * identityNotifications.createNotification(
         *         userId,
         *         "PAYMENT",
         *         message
         * );
         *
         * identityNotifications.createAuditEvent(
         *         userId,
         *         "FOREX_CARD_LOAD_COMPLETED",
         *         "FOREX_CARD",
         *         String.valueOf(card.cardId())
         * );
         *
         * New approach:
         * Publish the completed card-load event only after
         * the Forex database transaction commits.
         */
        ForexIdentityEvent event = new ForexIdentityEvent(
                userId,
                userId,
                "PAYMENT",
                message,
                "FOREX_CARD_LOAD_COMPLETED",
                "FOREX_CARD",
                String.valueOf(card.cardId()),
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