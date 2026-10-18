package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreditCardEmiPlanRepository {

    private final JdbcTemplate jdbcTemplate;

    public CreditCardEmiPlanRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<EmiCardDetails> findActiveCreditCard(
            Long userId,
            Long cardId) {

        return jdbcTemplate.query("""
                SELECT card_id,
                       outstanding_amount,
                       payment_due_date
                  FROM cards
                 WHERE card_id = ?
                   AND user_id = ?
                   AND card_type = 'CREDIT'
                   AND status = 'ACTIVE'
                """,
                (row, index) -> new EmiCardDetails(
                        row.getLong("card_id"),
                        row.getBigDecimal("outstanding_amount"),
                        row.getDate("payment_due_date") == null
                                ? null
                                : row.getDate("payment_due_date")
                                        .toLocalDate()
                ),
                cardId,
                userId
        ).stream().findFirst();
    }

    public BigDecimal activeConvertedAmount(
            Long userId,
            Long cardId) {

        BigDecimal amount = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(remaining_principal), 0)
                  FROM credit_card_emi_plans
                 WHERE user_id = ?
                   AND card_id = ?
                   AND status = 'ACTIVE'
                """,
                BigDecimal.class,
                userId,
                cardId
        );

        return amount == null ? BigDecimal.ZERO : amount;
    }

    public Long createPlan(
            Long userId,
            Long cardId,
            BigDecimal conversionAmount,
            BigDecimal annualInterestRate,
            Integer tenureMonths,
            BigDecimal emiAmount,
            BigDecimal totalPayable,
            LocalDate firstDueDate) {

        jdbcTemplate.update("""
                INSERT INTO credit_card_emi_plans (
                    card_id,
                    user_id,
                    conversion_amount,
                    annual_interest_rate,
                    tenure_months,
                    emi_amount,
                    total_payable,
                    remaining_principal,
                    next_due_date,
                    status,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', SYSTIMESTAMP)
                """,
                cardId,
                userId,
                conversionAmount,
                annualInterestRate,
                tenureMonths,
                emiAmount,
                totalPayable,
                conversionAmount,
                Date.valueOf(firstDueDate)
        );

        return jdbcTemplate.queryForObject("""
                SELECT MAX(credit_card_emi_plan_id)
                  FROM credit_card_emi_plans
                 WHERE user_id = ?
                   AND card_id = ?
                """,
                Long.class,
                userId,
                cardId
        );
    }

    public void createSchedule(
            Long emiPlanId,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal openingPrincipal,
            BigDecimal emiAmount,
            BigDecimal principalComponent,
            BigDecimal interestComponent,
            BigDecimal closingPrincipal) {

        jdbcTemplate.update("""
                INSERT INTO credit_card_emi_schedules (
                    credit_card_emi_plan_id,
                    installment_number,
                    due_date,
                    opening_principal,
                    emi_amount,
                    principal_component,
                    interest_component,
                    closing_principal,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """,
                emiPlanId,
                installmentNumber,
                Date.valueOf(dueDate),
                openingPrincipal,
                emiAmount,
                principalComponent,
                interestComponent,
                closingPrincipal
        );
    }

    public List<CreditCardEmiPlanResponse> findPlans(
            Long userId) {

        return jdbcTemplate.query("""
                SELECT credit_card_emi_plan_id,
                       card_id,
                       conversion_amount,
                       annual_interest_rate,
                       tenure_months,
                       emi_amount,
                       total_payable,
                       remaining_principal,
                       next_due_date,
                       status
                  FROM credit_card_emi_plans
                 WHERE user_id = ?
                 ORDER BY created_at DESC
                """,
                (row, index) -> new CreditCardEmiPlanResponse(
                        row.getLong("credit_card_emi_plan_id"),
                        row.getLong("card_id"),
                        row.getBigDecimal("conversion_amount"),
                        row.getBigDecimal("annual_interest_rate"),
                        row.getInt("tenure_months"),
                        row.getBigDecimal("emi_amount"),
                        row.getBigDecimal("total_payable"),
                        row.getBigDecimal("remaining_principal"),
                        row.getDate("next_due_date") == null
                                ? null
                                : row.getDate("next_due_date")
                                        .toLocalDate(),
                        row.getString("status")
                ),
                userId
        );
    }

    public Optional<CreditCardEmiPlanResponse> findPlan(
            Long userId,
            Long emiPlanId) {

        return jdbcTemplate.query("""
                SELECT credit_card_emi_plan_id,
                       card_id,
                       conversion_amount,
                       annual_interest_rate,
                       tenure_months,
                       emi_amount,
                       total_payable,
                       remaining_principal,
                       next_due_date,
                       status
                  FROM credit_card_emi_plans
                 WHERE credit_card_emi_plan_id = ?
                   AND user_id = ?
                """,
                (row, index) -> new CreditCardEmiPlanResponse(
                        row.getLong("credit_card_emi_plan_id"),
                        row.getLong("card_id"),
                        row.getBigDecimal("conversion_amount"),
                        row.getBigDecimal("annual_interest_rate"),
                        row.getInt("tenure_months"),
                        row.getBigDecimal("emi_amount"),
                        row.getBigDecimal("total_payable"),
                        row.getBigDecimal("remaining_principal"),
                        row.getDate("next_due_date") == null
                                ? null
                                : row.getDate("next_due_date")
                                        .toLocalDate(),
                        row.getString("status")
                ),
                emiPlanId,
                userId
        ).stream().findFirst();
    }

    public List<CreditCardEmiScheduleResponse> findSchedule(
            Long emiPlanId) {

        return jdbcTemplate.query("""
                SELECT credit_card_emi_schedule_id,
                       installment_number,
                       due_date,
                       opening_principal,
                       emi_amount,
                       principal_component,
                       interest_component,
                       closing_principal,
                       status,
                       paid_at
                  FROM credit_card_emi_schedules
                 WHERE credit_card_emi_plan_id = ?
                 ORDER BY installment_number
                """,
                (row, index) -> new CreditCardEmiScheduleResponse(
                        row.getLong("credit_card_emi_schedule_id"),
                        row.getInt("installment_number"),
                        row.getDate("due_date").toLocalDate(),
                        row.getBigDecimal("opening_principal"),
                        row.getBigDecimal("emi_amount"),
                        row.getBigDecimal("principal_component"),
                        row.getBigDecimal("interest_component"),
                        row.getBigDecimal("closing_principal"),
                        row.getString("status"),
                        row.getTimestamp("paid_at") == null
                                ? null
                                : row.getTimestamp("paid_at")
                                        .toLocalDateTime()
                ),
                emiPlanId
        );
    }

    public record EmiCardDetails(
            Long cardId,
            BigDecimal outstandingAmount,
            LocalDate paymentDueDate) {
    }
}