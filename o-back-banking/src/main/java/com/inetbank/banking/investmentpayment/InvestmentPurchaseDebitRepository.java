package com.inetbank.banking.investmentpayment;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvestmentPurchaseDebitRepository {

    private final JdbcTemplate jdbcTemplate;

    public InvestmentPurchaseDebitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ExistingTransaction> findByReference(
            String referenceNumber) {

        return jdbcTemplate.query(
                """
                SELECT transaction_id,
                       account_id,
                       transaction_type,
                       entry_side,
                       amount,
                       status
                FROM transactions
                WHERE reference_number = ?
                """,
                (row, index) -> new ExistingTransaction(
                        row.getLong("transaction_id"),
                        row.getLong("account_id"),
                        row.getString("transaction_type"),
                        row.getString("entry_side"),
                        row.getBigDecimal("amount"),
                        row.getString("status")),
                referenceNumber)
                .stream()
                .findFirst();
    }

    public boolean debitAccount(
            Long accountId,
            Long userId,
            BigDecimal amount) {

        return jdbcTemplate.update(
                """
                UPDATE accounts
                SET book_balance = book_balance - ?,
                    available_balance = available_balance - ?
                WHERE account_id = ?
                  AND user_id = ?
                  AND status = 'ACTIVE'
                  AND book_balance >= ?
                  AND available_balance >= ?
                """,
                amount,
                amount,
                accountId,
                userId,
                amount,
                amount) == 1;
    }

    public Long createDebitTransaction(
            Long accountId,
            BigDecimal amount,
            String referenceNumber) {

        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    payment_order_id,
                    account_id,
                    transaction_type,
                    entry_side,
                    amount,
                    reference_number,
                    status,
                    transaction_date
                )
                VALUES (
                    NULL,
                    ?,
                    'INVESTMENT_PURCHASE',
                    'DEBIT',
                    ?,
                    ?,
                    'SUCCESS',
                    SYSTIMESTAMP
                )
                """,
                accountId,
                amount,
                referenceNumber);

        return jdbcTemplate.queryForObject(
                """
                SELECT transaction_id
                FROM transactions
                WHERE reference_number = ?
                """,
                Long.class,
                referenceNumber);
    }

    public record ExistingTransaction(
            Long transactionId,
            Long accountId,
            String transactionType,
            String entrySide,
            BigDecimal amount,
            String status) {
    }
}