package com.example.inetbank.forex.conversion;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ForexTransactionHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexTransactionHistoryRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ForexTransactionResponse> findForUser(
            Long userId,
            String currency,
            String transactionType,
            String status,
            LocalDate from,
            LocalDate to) {

        StringBuilder sql = new StringBuilder("""
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
                """);

        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);

        if (currency != null && !currency.isBlank()) {
            String value = currency.trim().toUpperCase();

            sql.append("""
                     AND (
                         from_currency = ?
                         OR to_currency = ?
                     )
                    """);

            parameters.add(value);
            parameters.add(value);
        }

        if (transactionType != null
                && !transactionType.isBlank()) {

            sql.append(" AND transaction_type = ?");

            parameters.add(
                    transactionType.trim().toUpperCase()
            );
        }

        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");

            parameters.add(
                    status.trim().toUpperCase()
            );
        }

        if (from != null) {
            sql.append(" AND created_at >= ?");

            parameters.add(
                    Timestamp.valueOf(from.atStartOfDay())
            );
        }

        if (to != null) {
            sql.append(" AND created_at < ?");

            parameters.add(
                    Timestamp.valueOf(
                            to.plusDays(1).atStartOfDay()
                    )
            );
        }

        sql.append("""
                 ORDER BY created_at DESC,
                          forex_transaction_id DESC
                """);

        return jdbcTemplate.query(
                sql.toString(),
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
                parameters.toArray()
        );
    }
}