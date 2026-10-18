package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreditCardEmiReminderRepository {

    public static final String REMINDER_TYPE =
            "DUE_IN_3_DAYS";

    private final JdbcTemplate jdbcTemplate;

    public CreditCardEmiReminderRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EmiReminderCandidate> findDueForReminder(
            LocalDate dueDate) {

        return jdbcTemplate.query("""
                SELECT s.credit_card_emi_schedule_id,
                       s.credit_card_emi_plan_id,
                       s.installment_number,
                       s.due_date,
                       s.emi_amount,
                       p.user_id,
                       p.card_id,
                       c.last_four
                  FROM credit_card_emi_schedules s
                  JOIN credit_card_emi_plans p
                    ON p.credit_card_emi_plan_id =
                       s.credit_card_emi_plan_id
                  JOIN cards c
                    ON c.card_id = p.card_id
                 WHERE s.status = 'PENDING'
                   AND s.due_date = ?
                   AND NOT EXISTS (
                       SELECT 1
                         FROM credit_card_emi_reminder_log l
                        WHERE l.credit_card_emi_schedule_id =
                              s.credit_card_emi_schedule_id
                          AND l.reminder_type = ?
                          AND l.delivery_channel = 'EMAIL'
                   )
                 ORDER BY s.due_date,
                          s.credit_card_emi_schedule_id
                """,
                (row, index) -> new EmiReminderCandidate(
                        row.getLong(
                                "credit_card_emi_schedule_id"
                        ),
                        row.getLong(
                                "credit_card_emi_plan_id"
                        ),
                        row.getLong("user_id"),
                        row.getLong("card_id"),
                        row.getString("last_four"),
                        row.getInt("installment_number"),
                        row.getObject(
                                "due_date",
                                LocalDate.class
                        ),
                        row.getBigDecimal("emi_amount")
                ),
                Date.valueOf(dueDate),
                REMINDER_TYPE
        );
    }

    public boolean recordEmailReminder(
            Long scheduleId,
            LocalDate dueDate) {

        try {
            return jdbcTemplate.update("""
                    INSERT INTO credit_card_emi_reminder_log (
                        credit_card_emi_schedule_id,
                        reminder_type,
                        due_date,
                        delivery_channel,
                        delivered_at
                    )
                    VALUES (?, ?, ?, 'EMAIL', SYSTIMESTAMP)
                    """,
                    scheduleId,
                    REMINDER_TYPE,
                    Date.valueOf(dueDate)
            ) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public record EmiReminderCandidate(
            Long scheduleId,
            Long emiPlanId,
            Long userId,
            Long cardId,
            String cardLastFour,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal emiAmount
    ) {
    }
}