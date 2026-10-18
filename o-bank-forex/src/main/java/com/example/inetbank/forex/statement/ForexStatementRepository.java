package com.example.inetbank.forex.statement;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;

@Repository
public class ForexStatementRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexStatementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countTransactions(
            Long userId,
            LocalDate from,
            LocalDate to) {

        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM forex_transactions
                 WHERE user_id = ?
                   AND created_at >= ?
                   AND created_at < ?
                """,
                Long.class,
                userId,
                Timestamp.valueOf(from.atStartOfDay()),
                Timestamp.valueOf(to.plusDays(1).atStartOfDay())
        );

        return count == null ? 0 : count;
    }

    public List<ForexTransactionResponse> findTransactions(
            Long userId,
            LocalDate from,
            LocalDate to,
            int page,
            int pageSize) {

        List<Object> parameters = new ArrayList<>();

        parameters.add(userId);
        parameters.add(Timestamp.valueOf(from.atStartOfDay()));
        parameters.add(Timestamp.valueOf(
                to.plusDays(1).atStartOfDay()
        ));
        parameters.add((long) page * pageSize);
        parameters.add(pageSize);

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
                   AND created_at >= ?
                   AND created_at < ?
                 ORDER BY created_at DESC
                 OFFSET ? ROWS
                 FETCH NEXT ? ROWS ONLY
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
                parameters.toArray()
        );
    }
}