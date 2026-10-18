package com.example.inetbank.credit.cardmanagement;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreditCardActivityRepository {

    private static final String ACTIVITY_SQL = """
            SELECT 'MERCHANT-' || p.merchant_purchase_id
                       AS activity_key,
                   'MERCHANT_PURCHASE'
                       AS activity_type,
                   'CHARGE'
                       AS direction,
                   p.card_charge_amount
                       AS amount,
                   p.reference_number
                       AS reference_number,
                   p.status
                       AS status,
                   'Merchant purchase at ' || m.merchant_name
                       AS description,
                   p.purchased_at
                       AS occurred_at
              FROM credit_card_merchant_purchases p
              JOIN merchants m
                ON m.merchant_id = p.merchant_id
             WHERE p.card_id = ?

            UNION ALL

            SELECT 'MERCHANT-REVERSAL-' || p.merchant_purchase_id
                       AS activity_key,
                   'MERCHANT_REVERSAL'
                       AS activity_type,
                   'REVERSAL'
                       AS direction,
                   p.card_charge_amount
                       AS amount,
                   p.reversal_reference_number
                       AS reference_number,
                   'SUCCESS'
                       AS status,
                   'Merchant purchase reversed at '
                       || m.merchant_name
                       AS description,
                   p.reversed_at
                       AS occurred_at
              FROM credit_card_merchant_purchases p
              JOIN merchants m
                ON m.merchant_id = p.merchant_id
             WHERE p.card_id = ?
               AND p.status = 'REVERSED'
               AND p.reversed_at IS NOT NULL

            UNION ALL

            SELECT 'CARD-REPAYMENT-' || r.card_repayment_id
                       AS activity_key,
                   'CARD_REPAYMENT'
                       AS activity_type,
                   'PAYMENT'
                       AS direction,
                   r.amount
                       AS amount,
                   r.reference_number
                       AS reference_number,
                   'SUCCESS'
                       AS status,
                   'Credit-card outstanding repayment'
                       AS description,
                   r.payment_date
                       AS occurred_at
              FROM card_repayments r
             WHERE r.card_id = ?

            UNION ALL

            SELECT 'BILL-PAYMENT-'
                       || b.credit_card_bill_payment_id
                       AS activity_key,
                   'CREDIT_CARD_BILL_PAYMENT'
                       AS activity_type,
                   'CHARGE'
                       AS direction,
                   b.amount
                       AS amount,
                   b.reference_number
                       AS reference_number,
                   'SUCCESS'
                       AS status,
                   'Credit-card bill payment: '
                       || b.consumer_reference
                       AS description,
                   b.payment_date
                       AS occurred_at
              FROM credit_card_bill_payments b
             WHERE b.card_id = ?

            UNION ALL

            SELECT 'EMI-PLAN-' || p.credit_card_emi_plan_id
                       AS activity_key,
                   'EMI_CONVERSION'
                       AS activity_type,
                   'CONVERSION'
                       AS direction,
                   p.conversion_amount
                       AS amount,
                   'EMI-PLAN-' || p.credit_card_emi_plan_id
                       AS reference_number,
                   p.status
                       AS status,
                   'Converted to ' || p.tenure_months
                       || '-month EMI at '
                       || p.annual_interest_rate
                       || '% annual interest'
                       AS description,
                   p.created_at
                       AS occurred_at
              FROM credit_card_emi_plans p
             WHERE p.card_id = ?

            UNION ALL

            SELECT 'EMI-REPAYMENT-'
                       || r.credit_card_emi_repayment_id
                       AS activity_key,
                   'EMI_REPAYMENT'
                       AS activity_type,
                   'PAYMENT'
                       AS direction,
                   r.amount
                       AS amount,
                   r.reference_number
                       AS reference_number,
                   'SUCCESS'
                       AS status,
                   'EMI installment payment'
                       AS description,
                   r.payment_date
                       AS occurred_at
              FROM credit_card_emi_repayments r
              JOIN credit_card_emi_plans p
                ON p.credit_card_emi_plan_id =
                   r.credit_card_emi_plan_id
             WHERE p.card_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public CreditCardActivityRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public long countActivities(Long cardId) {

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + ACTIVITY_SQL + ")",
                Long.class,
                cardId,
                cardId,
                cardId,
                cardId,
                cardId,
                cardId
        );

        return count == null ? 0L : count;
    }

    public List<CreditCardActivityResponse> findPage(
            Long cardId,
            int page,
            int size) {

        return jdbcTemplate.query("""
                SELECT activity_key,
                       activity_type,
                       direction,
                       amount,
                       reference_number,
                       status,
                       description,
                       occurred_at
                  FROM (
                """ + ACTIVITY_SQL + """
                  )
                 ORDER BY occurred_at DESC NULLS LAST,
                          activity_key DESC
                 OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """,
                (row, index) -> new CreditCardActivityResponse(
                        row.getString("activity_key"),
                        row.getString("activity_type"),
                        row.getString("direction"),
                        row.getBigDecimal("amount"),
                        row.getString("reference_number"),
                        row.getString("status"),
                        row.getString("description"),
                        dateTime(
                                row.getTimestamp("occurred_at")
                        )
                ),
                cardId,
                cardId,
                cardId,
                cardId,
                cardId,
                cardId,
                page * size,
                size
        );
    }

    private LocalDateTime dateTime(Timestamp timestamp) {
        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }
}