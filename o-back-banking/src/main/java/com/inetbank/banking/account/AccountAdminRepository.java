package com.inetbank.banking.account;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountAdminRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountAdminRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AccountAdminDetails> findById(
            Long accountId) {

        return jdbcTemplate.query("""
                SELECT account_id,
                       user_id,
                       account_number,
                       status
                  FROM accounts
                 WHERE account_id = ?
                """,
                (row, index) -> new AccountAdminDetails(
                        row.getLong("account_id"),
                        row.getLong("user_id"),
                        row.getString("account_number"),
                        row.getString("status")
                ),
                accountId
        ).stream().findFirst();
    }

    public boolean updateStatus(
            Long accountId,
            String expectedStatus,
            String newStatus) {

        return jdbcTemplate.update("""
                UPDATE accounts
                   SET status = ?
                 WHERE account_id = ?
                   AND status = ?
                """,
                newStatus,
                accountId,
                expectedStatus
        ) == 1;
    }

    public record AccountAdminDetails(
            Long accountId,
            Long customerUserId,
            String accountNumber,
            String status) {
    }
}