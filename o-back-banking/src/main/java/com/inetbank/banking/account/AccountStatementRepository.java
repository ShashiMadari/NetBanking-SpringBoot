package com.inetbank.banking.account;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountStatementRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountStatementRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean accountBelongsToCustomer(
            Long accountId,
            Long userId) {

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM accounts
                 WHERE account_id = ?
                   AND user_id = ?
                """,
                Integer.class,
                accountId,
                userId
        );

        return count != null && count > 0;
    }

    public List<AccountStatementResponse> findForCustomerAccount(
            Long accountId,
            Long userId) {

        String sql = """
                SELECT t.transaction_id,
                       t.transaction_type,
                       t.entry_side,
                       t.amount,
                       t.reference_number,
                       t.status,
                       t.transaction_date
                  FROM transactions t
                  JOIN accounts a
                    ON a.account_id = t.account_id
                 WHERE t.account_id = ?
                   AND a.user_id = ?
                 ORDER BY t.transaction_date DESC,
                          t.transaction_id DESC
                """;

        return jdbcTemplate.query(
                sql,
                (row, index) -> map(row),
                accountId,
                userId
        );
    }

    public long countForCustomerAccount(
            Long accountId,
            Long userId,
            LocalDate from,
            LocalDate to) {

        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                  FROM transactions t
                  JOIN accounts a
                    ON a.account_id = t.account_id
                 WHERE t.account_id = ?
                   AND a.user_id = ?
                """);

        List<Object> parameters = parameters(
                accountId,
                userId,
                from,
                to,
                sql
        );

        Long count = jdbcTemplate.queryForObject(
                sql.toString(),
                Long.class,
                parameters.toArray()
        );

        return count == null ? 0 : count;
    }

    public List<AccountStatementResponse> findPageForCustomerAccount(
            Long accountId,
            Long userId,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {

        StringBuilder sql = new StringBuilder("""
                SELECT t.transaction_id,
                       t.transaction_type,
                       t.entry_side,
                       t.amount,
                       t.reference_number,
                       t.status,
                       t.transaction_date
                  FROM transactions t
                  JOIN accounts a
                    ON a.account_id = t.account_id
                 WHERE t.account_id = ?
                   AND a.user_id = ?
                """);

        List<Object> parameters = parameters(
                accountId,
                userId,
                from,
                to,
                sql
        );

        sql.append("""
                 ORDER BY t.transaction_date DESC,
                          t.transaction_id DESC
                 OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """);

        parameters.add(page * size);
        parameters.add(size);

        return jdbcTemplate.query(
                sql.toString(),
                (row, index) -> map(row),
                parameters.toArray()
        );
    }

    public List<AccountStatementResponse> findMonthlyForCustomerAccount(
                Long accountId,
                Long userId,
                YearMonth statementMonth) {

        Timestamp from = Timestamp.valueOf(
                statementMonth.atDay(1).atStartOfDay()
        );

        Timestamp to = Timestamp.valueOf(
                statementMonth.plusMonths(1)
                        .atDay(1)
                        .atStartOfDay()
        );

        return jdbcTemplate.query("""
                SELECT t.transaction_id,
                        t.transaction_type,
                        t.entry_side,
                        t.amount,
                        t.reference_number,
                        t.status,
                        t.transaction_date
                FROM transactions t
                JOIN accounts a
                        ON a.account_id = t.account_id
                WHERE t.account_id = ?
                AND a.user_id = ?
                AND t.transaction_date >= ?
                AND t.transaction_date < ?
                ORDER BY t.transaction_date,
                        t.transaction_id
                """,
                (row, index) -> map(row),
                accountId,
                userId,
                from,
                to
        );
        }

    private List<Object> parameters(
            Long accountId,
            Long userId,
            LocalDate from,
            LocalDate to,
            StringBuilder sql) {

        List<Object> parameters = new ArrayList<>();

        parameters.add(accountId);
        parameters.add(userId);

        if (from != null && to != null) {
            sql.append("""
                     AND t.transaction_date >= ?
                     AND t.transaction_date < ?
                    """);

            parameters.add(
                    Timestamp.valueOf(from.atStartOfDay())
            );

            parameters.add(
                    Timestamp.valueOf(
                            to.plusDays(1).atStartOfDay()
                    )
            );
        }

        return parameters;
    }

    private AccountStatementResponse map(
            java.sql.ResultSet row) throws java.sql.SQLException {

        return new AccountStatementResponse(
                row.getLong("transaction_id"),
                row.getString("transaction_type"),
                row.getString("entry_side"),
                row.getBigDecimal("amount"),
                row.getString("reference_number"),
                row.getString("status"),
                row.getTimestamp("transaction_date")
                        .toLocalDateTime()
        );
    }
}