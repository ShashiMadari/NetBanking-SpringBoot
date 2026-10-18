package com.example.inetbank.credit.loan;

import java.time.YearMonth;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoanStatementDeliveryRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoanStatementDeliveryRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ActiveLoan> findActiveLoans() {
        return jdbcTemplate.query("""
                SELECT loan_id,
                       user_id
                  FROM loans
                 WHERE status = 'ACTIVE'
                 ORDER BY loan_id
                """,
                (row, index) -> new ActiveLoan(
                        row.getLong("loan_id"),
                        row.getLong("user_id")
                )
        );
    }

    public boolean alreadyDelivered(
            Long loanId,
            YearMonth statementMonth) {

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM loan_statement_delivery_log
                 WHERE loan_id = ?
                   AND statement_year = ?
                   AND statement_month = ?
                   AND channel = 'EMAIL'
                """,
                Integer.class,
                loanId,
                statementMonth.getYear(),
                statementMonth.getMonthValue()
        );

        return count != null && count > 0;
    }

    public void recordEmailDelivery(
            Long loanId,
            YearMonth statementMonth) {

        jdbcTemplate.update("""
                INSERT INTO loan_statement_delivery_log (
                    loan_id,
                    statement_year,
                    statement_month,
                    channel,
                    delivered_at
                )
                VALUES (?, ?, ?, 'EMAIL', SYSTIMESTAMP)
                """,
                loanId,
                statementMonth.getYear(),
                statementMonth.getMonthValue()
        );
    }

    public record ActiveLoan(
            Long loanId,
            Long customerUserId) {
    }
}