package com.example.inetbank.forex.remittance;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;

@Repository
public class ForexOutwardRemittanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexOutwardRemittanceRepository(
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
                (row, index) -> mapTransaction(row),
                userId,
                idempotencyKey
        ).stream().findFirst();
    }

    public Beneficiary findBeneficiaryForUpdate(
            Long userId,
            Long beneficiaryId) {

        return jdbcTemplate.query("""
                SELECT forex_beneficiary_id,
                       currency_code,
                       status
                  FROM forex_beneficiaries
                 WHERE forex_beneficiary_id = ?
                   AND user_id = ?
                 FOR UPDATE
                """,
                (row, index) -> new Beneficiary(
                        row.getLong("forex_beneficiary_id"),
                        row.getString("currency_code"),
                        row.getString("status")
                ),
                beneficiaryId,
                userId
        ).stream().findFirst().orElse(null);
    }

    public Wallet findWalletForUpdate(
            Long userId,
            String currencyCode) {

        return jdbcTemplate.query("""
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
        ).stream().findFirst().orElse(null);
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

    public BigDecimal findActiveRate(
            String currencyCode) {

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
                    'OUTWARD_REMITTANCE',
                    ?,
                    NULL,
                    ?,
                    NULL,
                    NULL,
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

    public void createLedgerEntry(
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

    public void createRemittance(
            Long transactionId,
            Long beneficiaryId,
            String purpose,
            String referenceNumber) {

        jdbcTemplate.update("""
                INSERT INTO forex_remittances (
                    forex_transaction_id,
                    forex_beneficiary_id,
                    direction,
                    purpose,
                    recipient_reference,
                    created_at
                )
                VALUES (?, ?, 'OUTWARD', ?, ?, SYSTIMESTAMP)
                """,
                transactionId,
                beneficiaryId,
                purpose,
                referenceNumber
        );
    }

    public ForexTransactionResponse findTransactionById(
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
                (row, index) -> mapTransaction(row),
                transactionId
        );
    }

    private ForexTransactionResponse mapTransaction(
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

    public record Beneficiary(
            Long beneficiaryId,
            String currencyCode,
            String status
    ) {
    }

    public record Wallet(
            Long walletId,
            BigDecimal availableBalance
    ) {
    }
}