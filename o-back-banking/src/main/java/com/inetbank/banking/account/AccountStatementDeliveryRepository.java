package com.inetbank.banking.account;

import java.time.YearMonth;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountStatementDeliveryRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountStatementDeliveryRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ActiveAccount> findActiveCustomerAccounts() {
        return jdbcTemplate.query("""
                SELECT account_id,
                       user_id,
                       account_number
                  FROM accounts
                 WHERE status = 'ACTIVE'
                 ORDER BY account_id
                """,
                (row, index) -> new ActiveAccount(
                        row.getLong("account_id"),
                        row.getLong("user_id"),
                        row.getString("account_number")
                )
        );
    }

    public boolean alreadyDelivered(
            Long accountId,
            YearMonth statementMonth) {

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM account_statement_delivery_log
                 WHERE account_id = ?
                   AND statement_year = ?
                   AND statement_month = ?
                   AND channel = 'EMAIL'
                """,
                Integer.class,
                accountId,
                statementMonth.getYear(),
                statementMonth.getMonthValue()
        );

        return count != null && count > 0;
    }

    public void recordEmailDelivery(
            Long accountId,
            YearMonth statementMonth) {

        jdbcTemplate.update("""
                INSERT INTO account_statement_delivery_log (
                    account_id,
                    statement_year,
                    statement_month,
                    channel,
                    delivered_at
                )
                VALUES (?, ?, ?, 'EMAIL', SYSTIMESTAMP)
                """,
                accountId,
                statementMonth.getYear(),
                statementMonth.getMonthValue()
        );
    }

    public record ActiveAccount(
            Long accountId,
            Long customerUserId,
            String accountNumber) {
    }
}