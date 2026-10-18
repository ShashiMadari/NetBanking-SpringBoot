package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreditCardMonthlyStatementRepository {

    private final JdbcTemplate jdbcTemplate;

    public CreditCardMonthlyStatementRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CardStatementDetails> findCardForUser(
            Long cardId,
            Long userId) {

        return jdbcTemplate.query("""
                SELECT c.card_id,
                       c.last_four,
                       c.outstanding_amount,
                       c.minimum_due_amount,
                       c.payment_due_date
                  FROM cards c
                 WHERE c.card_id = ?
                   AND c.user_id = ?
                   AND c.card_type = 'CREDIT'
                """,
                (row, index) -> new CardStatementDetails(
                        row.getLong("card_id"),
                        row.getString("last_four"),
                        row.getBigDecimal("outstanding_amount"),
                        row.getBigDecimal("minimum_due_amount"),
                        row.getObject(
                                "payment_due_date",
                                LocalDate.class
                        )
                ),
                cardId,
                userId
        ).stream().findFirst();
    }

    public List<CreditCardMonthlyStatementEntryResponse> findEntries(
            Long cardId,
            LocalDate fromDate,
            LocalDate toDateExclusive) {

        Timestamp from = Timestamp.valueOf(
                fromDate.atStartOfDay()
        );

        Timestamp to = Timestamp.valueOf(
                toDateExclusive.atStartOfDay()
        );

        return jdbcTemplate.query("""
                SELECT activity_type,
                       description,
                       reference_number,
                       amount,
                       occurred_at,
                       status
                  FROM (
                        SELECT 'MERCHANT_PURCHASE' AS activity_type,
                               'Merchant purchase at ' ||
                               m.merchant_name AS description,
                               p.reference_number,
                               p.card_charge_amount AS amount,
                               p.purchased_at AS occurred_at,
                               p.status
                          FROM credit_card_merchant_purchases p
                          JOIN merchants m
                            ON m.merchant_id = p.merchant_id
                         WHERE p.card_id = ?
                           AND p.purchased_at >= ?
                           AND p.purchased_at < ?

                        UNION ALL

                        SELECT 'CARD_REPAYMENT',
                               'Credit-card repayment',
                               o.reference_number,
                               o.amount,
                               o.completed_at,
                               o.status
                          FROM credit_card_payment_orders o
                         WHERE o.card_id = ?
                           AND o.status = 'SUCCESS'
                           AND o.completed_at >= ?
                           AND o.completed_at < ?

                        UNION ALL

                        SELECT 'BILL_PAYMENT',
                               'Credit-card bill payment',
                               o.reference_number,
                               o.amount,
                               o.completed_at,
                               o.status
                          FROM credit_card_bill_payment_orders o
                         WHERE o.card_id = ?
                           AND o.status = 'SUCCESS'
                           AND o.completed_at >= ?
                           AND o.completed_at < ?

                        UNION ALL

                        SELECT 'EMI_CONVERSION',
                               'Credit-card EMI plan created',
                               'EMI-PLAN-' ||
                               p.credit_card_emi_plan_id,
                               p.conversion_amount,
                               p.created_at,
                               p.status
                          FROM credit_card_emi_plans p
                         WHERE p.card_id = ?
                           AND p.created_at >= ?
                           AND p.created_at < ?

                        UNION ALL

                        SELECT 'EMI_PAYMENT',
                               'Credit-card EMI payment',
                               o.reference_number,
                               o.amount,
                               o.completed_at,
                               o.status
                          FROM credit_card_emi_payment_orders o
                          JOIN credit_card_emi_plans p
                            ON p.credit_card_emi_plan_id =
                               o.credit_card_emi_plan_id
                         WHERE p.card_id = ?
                           AND o.status = 'SUCCESS'
                           AND o.completed_at >= ?
                           AND o.completed_at < ?
                  )
                 ORDER BY occurred_at DESC
                """,
                (row, index) ->
                        new CreditCardMonthlyStatementEntryResponse(
                                row.getString("activity_type"),
                                row.getString("description"),
                                row.getString("reference_number"),
                                row.getBigDecimal("amount"),
                                row.getObject(
                                        "occurred_at",
                                        LocalDateTime.class
                                ),
                                row.getString("status")
                        ),
                cardId, from, to,
                cardId, from, to,
                cardId, from, to,
                cardId, from, to,
                cardId, from, to
        );
    }

    public record CardStatementDetails(
            Long cardId,
            String lastFour,
            BigDecimal outstandingAmount,
            BigDecimal minimumDueAmount,
            LocalDate paymentDueDate
    ) {
    }
}