package com.example.inetbank.forex.remittance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
public class ForexOutwardRemittanceService {

    private final ForexOutwardRemittanceRepository repository;

    // KAFKA CHANGE: Publish completed remittance event to Kafka.
    private final ForexKafkaEventPublisher kafkaEvents;

    // Old direct REST client, retained only as an explanation marker.
    // private final IdentitySystemNotificationClient identityNotifications;

    public ForexOutwardRemittanceService(
            ForexOutwardRemittanceRepository repository,

            // KAFKA CHANGE
            ForexKafkaEventPublisher kafkaEvents

            // IdentitySystemNotificationClient identityNotifications
    ) {
        this.repository = repository;
        this.kafkaEvents = kafkaEvents;

        // this.identityNotifications = identityNotifications;
    }

    @Transactional
    public ForexTransactionResponse remit(
            Long userId,
            OutwardRemittanceRequest request) {

        String idempotencyKey = normalizeIdempotency(
                request.idempotencyKey()
        );

        ForexTransactionResponse existing =
                repository.findByIdempotencyKey(
                        userId,
                        idempotencyKey
                ).orElse(null);

        if (existing != null) {
            return existing;
        }

        ForexOutwardRemittanceRepository.Beneficiary beneficiary =
                repository.findBeneficiaryForUpdate(
                        userId,
                        request.forexBeneficiaryId()
                );

        if (beneficiary == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Forex beneficiary was not found."
            );
        }

        if (!"ACTIVE".equals(beneficiary.status())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The selected Forex beneficiary is inactive."
            );
        }

        ForexOutwardRemittanceRepository.Wallet wallet =
                repository.findWalletForUpdate(
                        userId,
                        beneficiary.currencyCode()
                );

        if (wallet == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No Forex wallet exists for "
                            + beneficiary.currencyCode() + "."
            );
        }

        if (!repository.debitWallet(
                wallet.walletId(),
                request.amount())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient Forex wallet balance for this remittance."
            );
        }

        BigDecimal rate = repository.findActiveRate(
                beneficiary.currencyCode()
        );

        if (rate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The beneficiary currency is unavailable."
            );
        }

        BigDecimal inrEquivalent = request.amount()
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);

        String referenceNumber = referenceNumber(
                userId,
                idempotencyKey
        );

        Long transactionId = repository.createTransaction(
                userId,
                beneficiary.currencyCode(),
                request.amount(),
                inrEquivalent,
                referenceNumber,
                idempotencyKey
        );

        repository.createLedgerEntry(
                transactionId,
                wallet.walletId(),
                beneficiary.currencyCode(),
                request.amount(),
                wallet.availableBalance()
                        .subtract(request.amount())
        );

        repository.createRemittance(
                transactionId,
                beneficiary.beneficiaryId(),
                request.purpose().trim(),
                referenceNumber
        );

        String message = "Forex outward remittance of "
                + request.amount().toPlainString()
                + " "
                + beneficiary.currencyCode()
                + " was completed. Reference: "
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
         *         "FOREX_OUTWARD_REMITTANCE_COMPLETED",
         *         "FOREX_TRANSACTION",
         *         String.valueOf(transactionId)
         * );
         *
         * New approach:
         * Publish one event after the Forex database transaction commits.
         * Identity consumes the event and creates notification + audit row.
         */
        ForexIdentityEvent event = new ForexIdentityEvent(
                userId,
                userId,
                "PAYMENT",
                message,
                "FOREX_OUTWARD_REMITTANCE_COMPLETED",
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

        return repository.findTransactionById(
                transactionId
        );
    }

    private String normalizeIdempotency(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "idempotencyKey is required."
            );
        }

        String normalized = value.trim();

        if (normalized.length() > 64) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "idempotencyKey can contain at most 64 characters."
            );
        }

        return normalized;
    }

    private String referenceNumber(
            Long userId,
            String idempotencyKey) {

        String value = userId + "|" + idempotencyKey;

        String id = UUID.nameUUIDFromBytes(
                value.getBytes(StandardCharsets.UTF_8)
        ).toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();

        return "FXR-" + id;
    }
}