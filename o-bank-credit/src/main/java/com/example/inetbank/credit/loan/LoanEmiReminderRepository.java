package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoanEmiReminderRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoanEmiReminderRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DueEmiReminder> findDueOn(
            LocalDate dueDate) {

        return jdbcTemplate.query("""
                SELECT s.loan_emi_schedule_id,
                       l.loan_id,
                       l.user_id,
                       l.loan_account_number,
                       s.installment_number,
                       s.due_date,
                       s.emi_amount
                  FROM loan_emi_schedules s
                  JOIN loans l
                    ON l.loan_id = s.loan_id
                 WHERE l.status = 'ACTIVE'
                   AND s.status = 'DUE'
                   AND TRUNC(s.due_date) = ?
                 ORDER BY s.loan_emi_schedule_id
                """,
                (row, index) -> new DueEmiReminder(
                        row.getLong("loan_emi_schedule_id"),
                        row.getLong("loan_id"),
                        row.getLong("user_id"),
                        row.getString("loan_account_number"),
                        row.getInt("installment_number"),
                        row.getDate("due_date").toLocalDate(),
                        row.getBigDecimal("emi_amount")
                ),
                java.sql.Date.valueOf(dueDate)
        );
    }

    public boolean alreadySent(
            Long scheduleId) {

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM loan_emi_reminder_log
                 WHERE loan_emi_schedule_id = ?
                   AND reminder_type = 'DUE_IN_3_DAYS'
                   AND channel = 'EMAIL'
                """,
                Integer.class,
                scheduleId
        );

        return count != null && count > 0;
    }

    public void recordEmailReminder(
            Long scheduleId,
            LocalDate dueDate) {

        jdbcTemplate.update("""
                INSERT INTO loan_emi_reminder_log (
                    loan_emi_schedule_id,
                    reminder_type,
                    due_date,
                    channel,
                    sent_at
                )
                VALUES (?, 'DUE_IN_3_DAYS', ?, 'EMAIL', SYSTIMESTAMP)
                """,
                scheduleId,
                java.sql.Date.valueOf(dueDate)
        );
    }

    public record DueEmiReminder(
            Long scheduleId,
            Long loanId,
            Long customerUserId,
            String loanAccountNumber,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal emiAmount) {
    }
}