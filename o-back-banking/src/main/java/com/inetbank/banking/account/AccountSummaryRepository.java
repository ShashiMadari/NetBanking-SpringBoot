package com.inetbank.banking.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountSummaryRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountSummaryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AccountSummaryResponse> findForCustomerId(Long userId) {

        String sql = """
                SELECT a.account_id,
                       a.account_number,
                       a.account_type,
                       a.book_balance,
                       a.available_balance,
                       a.lien_amount,
                       a.currency,
                       a.status,
                       b.branch_name,
                       b.ifsc_code
                  FROM accounts a
                  LEFT JOIN branches b
                    ON b.branch_id = a.branch_id
                 WHERE a.user_id = ?
                 ORDER BY a.account_id
                """;

        return jdbcTemplate.query(
                sql,
                (row, index) -> map(row),
                userId
        );
    }

    public Optional<AccountSummaryResponse> findForCustomerIdAndAccountId(
            Long userId,
            Long accountId) {

        String sql = """
                SELECT a.account_id,
                       a.account_number,
                       a.account_type,
                       a.book_balance,
                       a.available_balance,
                       a.lien_amount,
                       a.currency,
                       a.status,
                       b.branch_name,
                       b.ifsc_code
                  FROM accounts a
                  LEFT JOIN branches b
                    ON b.branch_id = a.branch_id
                 WHERE a.user_id = ?
                   AND a.account_id = ?
                """;

        return jdbcTemplate.query(
                sql,
                (row, index) -> map(row),
                userId,
                accountId
        ).stream().findFirst();
    }

    private AccountSummaryResponse map(ResultSet row)
            throws SQLException {

        return new AccountSummaryResponse(
                row.getLong("account_id"),
                row.getString("account_number"),
                row.getString("account_type"),
                row.getBigDecimal("book_balance"),
                row.getBigDecimal("available_balance"),
                row.getBigDecimal("lien_amount"),
                row.getString("currency"),
                row.getString("status"),
                row.getString("branch_name"),
                row.getString("ifsc_code")
        );
    }
}