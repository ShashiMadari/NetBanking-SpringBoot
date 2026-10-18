package com.example.inetbank.forex.card;

import java.math.BigDecimal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;

@Repository
public class ForexCardLoadRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexCardLoadRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public Card findCardForUpdate(
            Long userId,
            Long cardId) {

        return jdbcTemplate.query("""
                SELECT c.forex_card_id,
                       c.wallet_id,
                       c.status,
                       c.available_balance,
                       w.currency_code
                  FROM forex_cards c
                  JOIN forex_wallets w
                    ON w.wallet_id = c.wallet_id
                 WHERE c.forex_card_id = ?
                   AND c.user_id = ?
                 FOR UPDATE
                """,
                (row, index) -> new Card(
                        row.getLong("forex_card_id"),
                        row.getLong("wallet_id"),
                        row.getString("status"),
                        row.getBigDecimal("available_balance"),
                        row.getString("currency_code")
                ),
                cardId,
                userId
        ).stream().findFirst().orElse(null);
    }

    public Wallet findWalletForUpdate(Long walletId) {
        return jdbcTemplate.queryForObject("""
                SELECT wallet_id,
                       available_balance
                  FROM forex_wallets
                 WHERE wallet_id = ?
                 FOR UPDATE
                """,
                (row, index) -> new Wallet(
                        row.getLong("wallet_id"),
                        row.getBigDecimal("available_balance")
                ),
                walletId
        );
    }

    public boolean debitWallet(
            Long walletId,
            BigDecimal amount) {

        return jdbcTemplate.update("""
                UPDATE forex_wallets
                   SET available_balance = available_balance - ?,
                       updated_at = SYSTIMESTAMP
                 WHERE wallet_id = ?
                   AND available_balance >= ?
                """,
                amount,
                walletId,
                amount
        ) == 1;
    }

    public void creditCard(
            Long cardId,
            BigDecimal amount) {

        jdbcTemplate.update("""
                UPDATE forex_cards
                   SET available_balance = available_balance + ?,
                       updated_at = SYSTIMESTAMP
                 WHERE forex_card_id = ?
                """,
                amount,
                cardId
        );
    }

    public BigDecimal findActiveRate(String currencyCode) {
        return jdbcTemplate.query("""
                SELECT current_rate_inr
                  FROM forex_rates
                 WHERE currency_code = ?
                   AND status = 'ACTIVE'
                """,
                (row, index) ->
                        row.getBigDecimal("current_rate_inr"),
                currencyCode
        ).stream().findFirst().orElse(null);
    }

    public Long createTransaction(
            Long userId,
            String currencyCode,
            BigDecimal amount,
            BigDecimal inrEquivalent,
            String referenceNumber) {

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
                    created_at,
                    completed_at
                )
                VALUES (
                    ?,
                    'CARD_LOAD',
                    ?,
                    ?,
                    ?,
                    ?,
                    NULL,
                    ?,
                    'WALLET_BALANCE',
                    'SUCCESS',
                    ?,
                    SYSTIMESTAMP,
                    SYSTIMESTAMP
                )
                """,
                userId,
                currencyCode,
                currencyCode,
                amount,
                amount,
                inrEquivalent,
                referenceNumber
        );

        return jdbcTemplate.queryForObject("""
                SELECT forex_transaction_id
                  FROM forex_transactions
                 WHERE reference_number = ?
                """,
                Long.class,
                referenceNumber
        );
    }

    public void createWalletDebitLedger(
            Long transactionId,
            Long walletId,
            String currencyCode,
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
                VALUES (?, ?, NULL, ?, 'DEBIT', ?, ?, SYSTIMESTAMP)
                """,
                transactionId,
                walletId,
                currencyCode,
                amount,
                balanceAfter
        );
    }

    public ForexTransactionResponse findTransaction(
            Long transactionId) {

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
                (row, index) -> new ForexTransactionResponse(
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
                ),
                transactionId
        );
    }

    public record Card(
            Long cardId,
            Long walletId,
            String status,
            BigDecimal availableBalance,
            String currencyCode
    ) {
    }

    public record Wallet(
            Long walletId,
            BigDecimal availableBalance
    ) {
    }
}