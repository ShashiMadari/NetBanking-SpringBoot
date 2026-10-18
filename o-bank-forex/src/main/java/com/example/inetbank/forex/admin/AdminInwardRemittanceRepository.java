package com.example.inetbank.forex.admin;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;

@Repository
public class AdminInwardRemittanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminInwardRemittanceRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
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

    public Long createTransaction(
            Long userId,
            String currencyCode,
            BigDecimal amount,
            BigDecimal inrEquivalent,
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
                    'INWARD_REMITTANCE',
                    NULL,
                    ?,
                    NULL,
                    ?,
                    NULL,
                    ?,
                    'INTERNATIONAL_PROVIDER',
                    'SUCCESS',
                    ?,
                    ?,
                    SYSTIMESTAMP,
                    SYSTIMESTAMP
                )
                """,
                userId,
                currencyCode,
                amount,
                inrEquivalent,
                referenceNumber,
                idempotencyKey
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

    public void createWalletCreditLedger(
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
                VALUES (?, ?, NULL, ?, 'CREDIT', ?, ?, SYSTIMESTAMP)
                """,
                transactionId,
                walletId,
                currencyCode,
                amount,
                balanceAfter
        );
    }

    public void createRemittance(
            Long transactionId,
            String purpose,
            String senderReference) {

        jdbcTemplate.update("""
                INSERT INTO forex_remittances (
                    forex_transaction_id,
                    forex_beneficiary_id,
                    direction,
                    purpose,
                    recipient_reference,
                    created_at
                )
                VALUES (?, NULL, 'INWARD', ?, ?, SYSTIMESTAMP)
                """,
                transactionId,
                purpose,
                senderReference
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

    public record Wallet(
            Long walletId,
            BigDecimal availableBalance
    ) {
    }
}