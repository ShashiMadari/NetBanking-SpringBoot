package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreditCardEmiPaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public CreditCardEmiPaymentRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PayableInstallment> findNextPayableInstallment(
            Long userId,
            Long emiPlanId) {

        return jdbcTemplate.query("""
                SELECT p.credit_card_emi_plan_id,
                       p.card_id,
                       p.remaining_principal,
                       c.last_four,
                       s.credit_card_emi_schedule_id,
                       s.emi_amount,
                       s.principal_component,
                       s.interest_component
                  FROM credit_card_emi_plans p
                  JOIN cards c
                    ON c.card_id = p.card_id
                  JOIN credit_card_emi_schedules s
                    ON s.credit_card_emi_plan_id =
                       p.credit_card_emi_plan_id
                 WHERE p.credit_card_emi_plan_id = ?
                   AND p.user_id = ?
                   AND p.status = 'ACTIVE'
                   AND s.status IN ('PENDING', 'OVERDUE')
                   AND s.installment_number = (
                       SELECT MIN(s2.installment_number)
                         FROM credit_card_emi_schedules s2
                        WHERE s2.credit_card_emi_plan_id =
                              p.credit_card_emi_plan_id
                          AND s2.status IN ('PENDING', 'OVERDUE')
                   )
                """,
                (row, index) -> new PayableInstallment(
                        row.getLong("credit_card_emi_plan_id"),
                        row.getLong("card_id"),
                        row.getBigDecimal("remaining_principal"),
                        row.getString("last_four"),
                        row.getLong("credit_card_emi_schedule_id"),
                        row.getBigDecimal("emi_amount"),
                        row.getBigDecimal("principal_component"),
                        row.getBigDecimal("interest_component")
                ),
                emiPlanId,
                userId
        ).stream().findFirst();
    }

    public boolean hasPendingOrder(
            Long userId,
            Long emiPlanId) {

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM credit_card_emi_payment_orders o
                  JOIN credit_card_emi_plans p
                    ON p.credit_card_emi_plan_id =
                       o.credit_card_emi_plan_id
                 WHERE o.credit_card_emi_plan_id = ?
                   AND p.user_id = ?
                   AND o.status = 'OTP_PENDING'
                """,
                Integer.class,
                emiPlanId,
                userId
        );

        return count != null && count > 0;
    }

    public Long createOrder(
            Long sourceAccountId,
            Long emiPlanId,
            BigDecimal amount,
            String referenceNumber,
            String otpHash,
            LocalDateTime otpExpiry) {

        String idempotencyKey = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO credit_card_emi_payment_orders (
                    source_account_id,
                    credit_card_emi_plan_id,
                    amount,
                    reference_number,
                    idempotency_key,
                    status,
                    otp_hash,
                    otp_expiry,
                    requested_at
                )
                VALUES (?, ?, ?, ?, ?, 'OTP_PENDING', ?, ?, SYSTIMESTAMP)
                """,
                sourceAccountId,
                emiPlanId,
                amount,
                referenceNumber,
                idempotencyKey,
                otpHash,
                Timestamp.valueOf(otpExpiry)
        );

        return jdbcTemplate.queryForObject("""
                SELECT credit_card_emi_payment_order_id
                  FROM credit_card_emi_payment_orders
                 WHERE idempotency_key = ?
                """,
                Long.class,
                idempotencyKey
        );
    }

    public Optional<EmiPaymentOrder> findOrder(
            Long userId,
            Long paymentOrderId) {

        return jdbcTemplate.query("""
                SELECT o.credit_card_emi_payment_order_id,
                       o.source_account_id,
                       o.credit_card_emi_plan_id,
                       o.amount,
                       o.reference_number,
                       o.status,
                       o.otp_hash,
                       o.otp_expiry,
                       o.requested_at,
                       o.completed_at
                  FROM credit_card_emi_payment_orders o
                  JOIN credit_card_emi_plans p
                    ON p.credit_card_emi_plan_id =
                       o.credit_card_emi_plan_id
                 WHERE o.credit_card_emi_payment_order_id = ?
                   AND p.user_id = ?
                """,
                (row, index) -> new EmiPaymentOrder(
                        row.getLong(
                                "credit_card_emi_payment_order_id"
                        ),
                        row.getLong("source_account_id"),
                        row.getLong("credit_card_emi_plan_id"),
                        row.getBigDecimal("amount"),
                        row.getString("reference_number"),
                        row.getString("status"),
                        row.getString("otp_hash"),
                        row.getTimestamp("otp_expiry") == null
                                ? null
                                : row.getTimestamp("otp_expiry")
                                        .toLocalDateTime(),
                        row.getTimestamp("requested_at")
                                .toLocalDateTime(),
                        row.getTimestamp("completed_at") == null
                                ? null
                                : row.getTimestamp("completed_at")
                                        .toLocalDateTime()
                ),
                paymentOrderId,
                userId
        ).stream().findFirst();
    }

    public void replaceOtp(
            Long userId,
            Long paymentOrderId,
            String otpHash,
            LocalDateTime otpExpiry) {

        jdbcTemplate.update("""
                UPDATE credit_card_emi_payment_orders
                   SET otp_hash = ?,
                       otp_expiry = ?
                 WHERE credit_card_emi_payment_order_id = ?
                   AND status = 'OTP_PENDING'
                   AND credit_card_emi_plan_id IN (
                       SELECT credit_card_emi_plan_id
                         FROM credit_card_emi_plans
                        WHERE user_id = ?
                   )
                """,
                otpHash,
                Timestamp.valueOf(otpExpiry),
                paymentOrderId,
                userId
        );
    }

    public void markExpired(
            Long userId,
            Long paymentOrderId) {

        jdbcTemplate.update("""
                UPDATE credit_card_emi_payment_orders
                   SET status = 'EXPIRED',
                       otp_hash = NULL,
                       otp_expiry = NULL
                 WHERE credit_card_emi_payment_order_id = ?
                   AND status = 'OTP_PENDING'
                   AND credit_card_emi_plan_id IN (
                       SELECT credit_card_emi_plan_id
                         FROM credit_card_emi_plans
                        WHERE user_id = ?
                   )
                """,
                paymentOrderId,
                userId
        );
    }

    public boolean completeOrder(
            Long userId,
            Long paymentOrderId) {

        return jdbcTemplate.update("""
                UPDATE credit_card_emi_payment_orders
                   SET status = 'SUCCESS',
                       otp_hash = NULL,
                       otp_expiry = NULL,
                       completed_at = SYSTIMESTAMP
                 WHERE credit_card_emi_payment_order_id = ?
                   AND status = 'OTP_PENDING'
                   AND credit_card_emi_plan_id IN (
                       SELECT credit_card_emi_plan_id
                         FROM credit_card_emi_plans
                        WHERE user_id = ?
                   )
                """,
                paymentOrderId,
                userId
        ) == 1;
    }

    public Long createRepayment(
            Long paymentOrderId,
            Long emiPlanId,
            Long sourceAccountId,
            BigDecimal amount,
            BigDecimal remainingBefore,
            BigDecimal remainingAfter,
            BigDecimal principalComponent,
            BigDecimal interestComponent,
            String referenceNumber) {

        jdbcTemplate.update("""
                INSERT INTO credit_card_emi_repayments (
                    credit_card_emi_payment_order_id,
                    credit_card_emi_plan_id,
                    source_account_id,
                    amount,
                    remaining_before,
                    remaining_after,
                    principal_component,
                    interest_component,
                    reference_number,
                    payment_date
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP)
                """,
                paymentOrderId,
                emiPlanId,
                sourceAccountId,
                amount,
                remainingBefore,
                remainingAfter,
                principalComponent,
                interestComponent,
                referenceNumber
        );

        return jdbcTemplate.queryForObject("""
                SELECT credit_card_emi_repayment_id
                  FROM credit_card_emi_repayments
                 WHERE reference_number = ?
                """,
                Long.class,
                referenceNumber
        );
    }

    public boolean markSchedulePaid(
            Long scheduleId,
            Long repaymentId) {

        return jdbcTemplate.update("""
                UPDATE credit_card_emi_schedules
                   SET status = 'PAID',
                       paid_at = SYSTIMESTAMP,
                       credit_card_emi_repayment_id = ?
                 WHERE credit_card_emi_schedule_id = ?
                   AND status IN ('PENDING', 'OVERDUE')
                """,
                repaymentId,
                scheduleId
        ) == 1;
    }

    public boolean updatePlanAfterPayment(
            Long userId,
            Long emiPlanId,
            BigDecimal principalComponent) {

        return jdbcTemplate.update("""
                UPDATE credit_card_emi_plans
                   SET remaining_principal =
                           remaining_principal - ?,
                       next_due_date = (
                           SELECT MIN(s.due_date)
                             FROM credit_card_emi_schedules s
                            WHERE s.credit_card_emi_plan_id =
                                  credit_card_emi_plans
                                      .credit_card_emi_plan_id
                              AND s.status IN ('PENDING', 'OVERDUE')
                       ),
                       status = CASE
                           WHEN remaining_principal - ? <= 0
                               THEN 'COMPLETED'
                           ELSE 'ACTIVE'
                       END,
                       completed_at = CASE
                           WHEN remaining_principal - ? <= 0
                               THEN SYSTIMESTAMP
                           ELSE NULL
                       END
                 WHERE credit_card_emi_plan_id = ?
                   AND user_id = ?
                   AND status = 'ACTIVE'
                   AND remaining_principal >= ?
                """,
                principalComponent,
                principalComponent,
                principalComponent,
                emiPlanId,
                userId,
                principalComponent
        ) == 1;
    }

    public boolean reduceCardOutstanding(
            Long userId,
            Long cardId,
            BigDecimal principalComponent) {

        return jdbcTemplate.update("""
                UPDATE cards
                   SET outstanding_amount =
                           GREATEST(
                               0,
                               outstanding_amount - ?
                           ),
                       minimum_due_amount =
                           GREATEST(
                               0,
                               LEAST(
                                   minimum_due_amount - ?,
                                   outstanding_amount - ?
                               )
                           )
                 WHERE card_id = ?
                   AND user_id = ?
                   AND card_type = 'CREDIT'
                   AND status = 'ACTIVE'
                   AND outstanding_amount >= ?
                """,
                principalComponent,
                principalComponent,
                principalComponent,
                cardId,
                userId,
                principalComponent
        ) == 1;
    }

    public record PayableInstallment(
            Long emiPlanId,
            Long cardId,
            BigDecimal remainingPrincipal,
            String cardLastFour,
            Long scheduleId,
            BigDecimal emiAmount,
            BigDecimal principalComponent,
            BigDecimal interestComponent) {
    }

    public record EmiPaymentOrder(
            Long paymentOrderId,
            Long sourceAccountId,
            Long emiPlanId,
            BigDecimal amount,
            String referenceNumber,
            String status,
            String otpHash,
            LocalDateTime otpExpiry,
            LocalDateTime requestedAt,
            LocalDateTime completedAt) {
    }
}