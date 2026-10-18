package com.example.inetbank.forex.conversion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.example.inetbank.forex.compliance.ComplianceFlagService;
import com.example.inetbank.forex.event.ForexKafkaEventPublisher;
import com.example.inetbank.forex.integration.BankingForexConversionClient;

// KAFKA CHANGE: Direct REST notification client is no longer used here.
// import com.example.inetbank.forex.integration.IdentitySystemNotificationClient;

@Service
public class ForexConversionService {

    private final ForexConversionRepository conversionRepository;
    private final BankingForexConversionClient bankingClient;
    private final ComplianceFlagService complianceFlags;

    // KAFKA CHANGE: Forex now publishes an event to Kafka.
    private final ForexKafkaEventPublisher kafkaEvents;

    // Old direct REST client, retained as a demo explanation marker.
    // private final IdentitySystemNotificationClient identityNotifications;

    public ForexConversionService(
            ForexConversionRepository conversionRepository,
            BankingForexConversionClient bankingClient,
            ComplianceFlagService complianceFlags,

            // KAFKA CHANGE: Inject Kafka publisher instead of Identity REST client.
            ForexKafkaEventPublisher kafkaEvents

            // IdentitySystemNotificationClient identityNotifications
    ) {
        this.conversionRepository = conversionRepository;
        this.bankingClient = bankingClient;
        this.complianceFlags = complianceFlags;
        this.kafkaEvents = kafkaEvents;

        // this.identityNotifications = identityNotifications;
    }

    @Transactional
    public ForexTransactionResponse convert(
            Long userId,
            ForexConversionRequest request) {

        String idempotencyKey = normalizeIdempotency(
                request.idempotencyKey()
        );

        ForexTransactionResponse existing =
                conversionRepository.findByIdempotencyKey(
                        userId,
                        idempotencyKey
                ).orElse(null);

        if (existing != null) {
            return existing;
        }

        ForexConversionRepository.Quote quote =
                conversionRepository.findQuoteForUpdate(
                        userId,
                        request.quoteId()
                ).orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Forex quote was not found."
                ));

        if (quote.isExpired()) {
            conversionRepository.markQuoteExpired(
                    quote.quoteId()
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This Forex quote has expired. Request a new quote."
            );
        }

        if (!"LOCKED".equals(quote.status())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This Forex quote was already used or cancelled."
            );
        }

        String paymentMethod = normalizePaymentMethod(
                request.paymentMethod()
        );

        String referenceNumber = referenceNumber(
                userId,
                idempotencyKey
        );

        if ("INR".equals(quote.fromCurrency())) {
            return buyForeignCurrency(
                    userId,
                    request.sourceAccountId(),
                    quote,
                    paymentMethod,
                    idempotencyKey,
                    referenceNumber
            );
        }

        if ("INR".equals(quote.toCurrency())) {
            return sellForeignCurrency(
                    userId,
                    request.sourceAccountId(),
                    quote,
                    paymentMethod,
                    idempotencyKey,
                    referenceNumber
            );
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only INR-to-foreign and foreign-to-INR "
                        + "Forex conversions are supported."
        );
    }

    private ForexTransactionResponse buyForeignCurrency(
            Long userId,
            Long sourceAccountId,
            ForexConversionRepository.Quote quote,
            String paymentMethod,
            String idempotencyKey,
            String referenceNumber) {

        if (!"INR_ACCOUNT".equals(paymentMethod)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "An INR Forex purchase must use "
                            + "paymentMethod INR_ACCOUNT."
            );
        }

        BigDecimal inrAmount = inrAmount(
                quote.fromAmount()
        );

        try {
            bankingClient.debitInrAccount(
                    userId,
                    sourceAccountId,
                    inrAmount,
                    referenceNumber
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Banking could not debit the selected INR account. "
                            + "Check that it is active and has "
                            + "sufficient balance."
            );
        }

        ForexConversionRepository.Wallet wallet =
                conversionRepository.getOrCreateWalletForUpdate(
                        userId,
                        quote.toCurrency()
                );

        conversionRepository.creditWallet(
                wallet.walletId(),
                quote.receiveAmount()
        );

        Long transactionId =
                conversionRepository.createSuccessfulTransaction(
                        userId,
                        quote.fromCurrency(),
                        quote.toCurrency(),
                        inrAmount,
                        quote.receiveAmount(),
                        quote.customerRateInr(),
                        inrAmount,
                        "INR_ACCOUNT",
                        referenceNumber,
                        idempotencyKey
                );

        conversionRepository.createLedgerEntry(
                transactionId,
                null,
                sourceAccountId,
                "INR",
                "DEBIT",
                inrAmount,
                null
        );

        conversionRepository.createLedgerEntry(
                transactionId,
                wallet.walletId(),
                null,
                quote.toCurrency(),
                "CREDIT",
                quote.receiveAmount(),
                wallet.availableBalance().add(
                        quote.receiveAmount()
                )
        );

        complianceFlags.flagHighValueTransaction(
                transactionId,
                inrAmount
        );

        conversionRepository.markQuoteUsed(
                quote.quoteId()
        );

        recordCompletedConversion(
                userId,
                transactionId,
                "CONVERSION_BUY",
                referenceNumber,
                inrAmount,
                quote.toCurrency(),
                quote.receiveAmount()
        );

        return conversionRepository.findById(
                transactionId
        );
    }

    private ForexTransactionResponse sellForeignCurrency(
            Long userId,
            Long destinationAccountId,
            ForexConversionRepository.Quote quote,
            String paymentMethod,
            String idempotencyKey,
            String referenceNumber) {

        if (!"FOREX_WALLET".equals(paymentMethod)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A Forex sale must use "
                            + "paymentMethod FOREX_WALLET."
            );
        }

        BigDecimal foreignAmount = quote.fromAmount();
        BigDecimal inrAmount = inrAmount(
                quote.receiveAmount()
        );

        ForexConversionRepository.WalletDebitResult wallet =
                conversionRepository.debitWalletForSell(
                        userId,
                        quote.fromCurrency(),
                        foreignAmount
                );

        if (wallet == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient Forex wallet balance."
            );
        }

        try {
            bankingClient.creditInrAccount(
                    userId,
                    destinationAccountId,
                    inrAmount,
                    referenceNumber
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Banking could not credit the selected INR account. "
                            + "Check that it is active and belongs to you."
            );
        }

        Long transactionId =
                conversionRepository.createSuccessfulSellTransaction(
                        userId,
                        quote.fromCurrency(),
                        quote.toCurrency(),
                        foreignAmount,
                        inrAmount,
                        quote.customerRateInr(),
                        referenceNumber,
                        idempotencyKey
                );

        conversionRepository.createSellLedgerEntries(
                transactionId,
                wallet.walletId(),
                quote.fromCurrency(),
                foreignAmount,
                wallet.balanceAfter(),
                destinationAccountId,
                inrAmount
        );

        complianceFlags.flagHighValueTransaction(
                transactionId,
                inrAmount
        );

        conversionRepository.markQuoteUsed(
                quote.quoteId()
        );

        recordCompletedConversion(
                userId,
                transactionId,
                "CONVERSION_SELL",
                referenceNumber,
                inrAmount,
                quote.fromCurrency(),
                foreignAmount
        );

        return conversionRepository.findById(
                transactionId
        );
    }

    private void recordCompletedConversion(
            Long userId,
            Long transactionId,
            String conversionType,
            String referenceNumber,
            BigDecimal inrAmount,
            String foreignCurrency,
            BigDecimal foreignAmount) {

        String message = conversionType.equals("CONVERSION_BUY")
                ? "Forex purchase completed. INR "
                        + inrAmount.toPlainString()
                        + " converted to "
                        + foreignAmount.toPlainString()
                        + " "
                        + foreignCurrency
                        + ". Reference: "
                        + referenceNumber
                : "Forex sale completed. "
                        + foreignAmount.toPlainString()
                        + " "
                        + foreignCurrency
                        + " converted to INR "
                        + inrAmount.toPlainString()
                        + ". Reference: "
                        + referenceNumber;

        /*
         * KAFKA CHANGE:
         *
         * Old approach:
         * Forex directly called Identity using REST.
         *
         * identityNotifications.createNotification(
         *         userId,
         *         "PAYMENT",
         *         message
         * );
         *
         * identityNotifications.createAuditEvent(
         *         userId,
         *         "FOREX_CONVERSION_COMPLETED",
         *         "FOREX_TRANSACTION",
         *         String.valueOf(transactionId)
         * );
         *
         * New approach:
         * Forex publishes one Kafka event only after this database
         * transaction successfully commits.
         * Identity will consume the event and create both records.
         */
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        kafkaEvents.publishConversionCompleted(
                                userId,
                                transactionId,
                                referenceNumber,
                                message
                        );
                    }
                }
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

    private String normalizePaymentMethod(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal inrAmount(BigDecimal amount) {
        try {
            return amount.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "INR conversion amount can contain at most "
                            + "two decimal places."
            );
        }
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
                .toUpperCase(Locale.ROOT);

        return "FX-" + id;
    }
}