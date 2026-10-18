package com.example.inetbank.forex.conversion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ForexConversionRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexConversionRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ForexTransactionResponse> findByIdempotencyKey(
            Long userId,
            String idempotencyKey) {

        return jdbcTemplate.query("""
                SELECT forex_transaction_id,
                       transaction_type,
                       from_currency,
                       to_currency,
                       from_amount,
                       receive_amount,
                       customer_rate_inr,
                       inr_equivalent,
                       payment_method,
                       status,
                       reference_number,
                       created_at
                  FROM forex_transactions
                 WHERE user_id = ?
                   AND idempotency_key = ?
                """,
                (row, index) -> transactionResponse(row),
                userId,
                idempotencyKey
        ).stream().findFirst();
    }

    public Optional<Quote> findQuoteForUpdate(
            Long userId,
            String quoteId) {

        return jdbcTemplate.query("""
                SELECT quote_id,
                       from_currency,
                       to_currency,
                       from_amount,
                       receive_amount,
                       customer_rate_inr,
                       markup_bps,
                       status,
                       expires_at
                  FROM forex_quotes
                 WHERE quote_id = ?
                   AND user_id = ?
                 FOR UPDATE
                """,
                (row, index) -> new Quote(
                        row.getString("quote_id"),
                        row.getString("from_currency"),
                        row.getString("to_currency"),
                        row.getBigDecimal("from_amount"),
                        row.getBigDecimal("receive_amount"),
                        row.getBigDecimal("customer_rate_inr"),
                        row.getInt("markup_bps"),
                        row.getString("status"),
                        row.getTimestamp("expires_at")
                                .toLocalDateTime()
                ),
                quoteId,
                userId
        ).stream().findFirst();
    }

    public void markQuoteExpired(String quoteId) {
        jdbcTemplate.update("""
                UPDATE forex_quotes
                   SET status = 'EXPIRED'
                 WHERE quote_id = ?
                """,
                quoteId
        );
    }

    public void markQuoteUsed(String quoteId) {
        jdbcTemplate.update("""
                UPDATE forex_quotes
                   SET status = 'USED',
                       used_at = SYSTIMESTAMP
                 WHERE quote_id = ?
                """,
                quoteId
        );
    }

    public Wallet getOrCreateWalletForUpdate(
            Long userId,
            String currencyCode) {

        jdbcTemplate.update("""
                MERGE INTO forex_wallets target
                USING (
                    SELECT ? AS user_id,
                           ? AS currency_code
                    FROM dual
                ) source
                ON (
                    target.user_id = source.user_id
                    AND target.currency_code = source.currency_code
                )
                WHEN NOT MATCHED THEN
                    INSERT (
                        user_id,
                        currency_code,
                        available_balance,
                        updated_at
                    )
                    VALUES (
                        source.user_id,
                        source.currency_code,
                        0,
                        SYSTIMESTAMP
                    )
                """,
                userId,
                currencyCode
        );

        return jdbcTemplate.queryForObject("""
                SELECT wallet_id,
                       available_balance
                  FROM forex_wallets
                 WHERE user_id = ?
                   AND currency_code = ?
                 FOR UPDATE
                """,
                (row, index) -> new Wallet(
                        row.getLong("wallet_id"),
                        row.getBigDecimal("available_balance")
                ),
                userId,
                currencyCode
        );
    }

    public void creditWallet(
            Long walletId,
            BigDecimal amount) {

        jdbcTemplate.update("""
                UPDATE forex_wallets
                   SET available_balance = available_balance + ?,
                       updated_at = SYSTIMESTAMP
                 WHERE wallet_id = ?
                """,
                amount,
                walletId
        );
    }

    public WalletDebitResult debitWalletForSell(
            Long userId,
            String currencyCode,
            BigDecimal amount) {

        WalletDebitResult wallet = jdbcTemplate.query("""
                SELECT wallet_id,
                       available_balance
                  FROM forex_wallets
                 WHERE user_id = ?
                   AND currency_code = ?
                 FOR UPDATE
                """,
                (row, index) -> new WalletDebitResult(
                        row.getLong("wallet_id"),
                        row.getBigDecimal("available_balance"),
                        null
                ),
                userId,
                currencyCode
        ).stream().findFirst().orElse(null);

        if (wallet == null
                || wallet.balanceBefore().compareTo(amount) < 0) {

            return null;
        }

        BigDecimal balanceAfter = wallet.balanceBefore()
                .subtract(amount);

        int updated = jdbcTemplate.update("""
                UPDATE forex_wallets
                   SET available_balance = ?,
                       updated_at = SYSTIMESTAMP
                 WHERE wallet_id = ?
                   AND available_balance >= ?
                """,
                balanceAfter,
                wallet.walletId(),
                amount
        );

        if (updated != 1) {
            return null;
        }

        return new WalletDebitResult(
                wallet.walletId(),
                wallet.balanceBefore(),
                balanceAfter
        );
    }

    public Long createSuccessfulTransaction(
            Long userId,
            String fromCurrency,
            String toCurrency,
            BigDecimal fromAmount,
            BigDecimal receiveAmount,
            BigDecimal customerRateInr,
            BigDecimal inrEquivalent,
            String paymentMethod,
            String referenceNumber,
            String idempotencyKey) {

        jdbcTemplate.update("""
                INSERT INTO forex_transactions (
                    user_id,
                    transaction_type,
                    from_currency,
                    to_currency,
                    from_amount,
                    receive_amount,
                    customer_rate_inr,
                    inr_equivalent,
                    payment_method,
                    status,
                    reference_number,
                    idempotency_key,
                    created_at,
                    completed_at
                )
                VALUES (
                    ?,
                    'CONVERSION_BUY',
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    'SUCCESS',
                    ?,
                    ?,
                    SYSTIMESTAMP,
                    SYSTIMESTAMP
                )
                """,
                userId,
                fromCurrency,
                toCurrency,
                fromAmount,
                receiveAmount,
                customerRateInr,
                inrEquivalent,
                paymentMethod,
                referenceNumber,
                idempotencyKey
        );

        return transactionIdByReference(referenceNumber);
    }

    public Long createSuccessfulSellTransaction(
            Long userId,
            String fromCurrency,
            String toCurrency,
            BigDecimal fromAmount,
            BigDecimal receiveAmount,
            BigDecimal customerRateInr,
            String referenceNumber,
            String idempotencyKey) {

        jdbcTemplate.update("""
                INSERT INTO forex_transactions (
                    user_id,
                    transaction_type,
                    from_currency,
                    to_currency,
                    from_amount,
                    receive_amount,
                    customer_rate_inr,
                    inr_equivalent,
                    payment_method,
                    status,
                    reference_number,
                    idempotency_key,
                    created_at,
                    completed_at
                )
                VALUES (
                    ?,
                    'CONVERSION_SELL',
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    'FOREX_WALLET',
                    'SUCCESS',
                    ?,
                    ?,
                    SYSTIMESTAMP,
                    SYSTIMESTAMP
                )
                """,
                userId,
                fromCurrency,
                toCurrency,
                fromAmount,
                receiveAmount,
                customerRateInr,
                receiveAmount,
                referenceNumber,
                idempotencyKey
        );

        return transactionIdByReference(referenceNumber);
    }

    public void createLedgerEntry(
            Long forexTransactionId,
            Long walletId,
            Long accountId,
            String currencyCode,
            String entrySide,
            BigDecimal amount,
            BigDecimal balanceAfter) {

        jdbcTemplate.update("""
                INSERT INTO forex_ledger_entries (
                    forex_transaction_id,
                    wallet_id,
                    account_id,
                    currency_code,
                    entry_side,
                    amount,
                    balance_after,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP)
                """,
                forexTransactionId,
                walletId,
                accountId,
                currencyCode,
                entrySide,
                amount,
                balanceAfter
        );
    }

    public void createSellLedgerEntries(
            Long forexTransactionId,
            Long walletId,
            String foreignCurrency,
            BigDecimal foreignAmount,
            BigDecimal walletBalanceAfter,
            Long bankAccountId,
            BigDecimal inrAmount) {

        createLedgerEntry(
                forexTransactionId,
                walletId,
                null,
                foreignCurrency,
                "DEBIT",
                foreignAmount,
                walletBalanceAfter
        );

        createLedgerEntry(
                forexTransactionId,
                null,
                bankAccountId,
                "INR",
                "CREDIT",
                inrAmount,
                null
        );
    }

    public ForexTransactionResponse findById(
            Long forexTransactionId) {

        return jdbcTemplate.queryForObject("""
                SELECT forex_transaction_id,
                       transaction_type,
                       from_currency,
                       to_currency,
                       from_amount,
                       receive_amount,
                       customer_rate_inr,
                       inr_equivalent,
                       payment_method,
                       status,
                       reference_number,
                       created_at
                  FROM forex_transactions
                 WHERE forex_transaction_id = ?
                """,
                (row, index) -> transactionResponse(row),
                forexTransactionId
        );
    }

    private Long transactionIdByReference(String referenceNumber) {
        return jdbcTemplate.queryForObject("""
                SELECT forex_transaction_id
                  FROM forex_transactions
                 WHERE reference_number = ?
                """,
                Long.class,
                referenceNumber
        );
    }

    private ForexTransactionResponse transactionResponse(
            java.sql.ResultSet row)
            throws java.sql.SQLException {

        return new ForexTransactionResponse(
                row.getLong("forex_transaction_id"),
                row.getString("transaction_type"),
                row.getString("from_currency"),
                row.getString("to_currency"),
                row.getBigDecimal("from_amount"),
                row.getBigDecimal("receive_amount"),
                row.getBigDecimal("customer_rate_inr"),
                row.getBigDecimal("inr_equivalent"),
                row.getString("payment_method"),
                row.getString("status"),
                row.getString("reference_number"),
                row.getTimestamp("created_at")
                        .toLocalDateTime()
        );
    }

    public record Quote(
            String quoteId,
            String fromCurrency,
            String toCurrency,
            BigDecimal fromAmount,
            BigDecimal receiveAmount,
            BigDecimal customerRateInr,
            Integer markupBps,
            String status,
            LocalDateTime expiresAt
    ) {
        public boolean isExpired() {
            return !expiresAt.isAfter(LocalDateTime.now());
        }
    }

    public record Wallet(
            Long walletId,
            BigDecimal availableBalance
    ) {
    }

    public record WalletDebitResult(
            Long walletId,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter
    ) {
    }
}