package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreditCardBillPaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public CreditCardBillPaymentRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CreditCardDetails> findActiveCreditCard(
            Long cardId,
            Long userId) {

        return jdbcTemplate.query("""
                SELECT card_id,
                       last_four,
                       credit_limit,
                       outstanding_amount,
                       minimum_due_amount
                  FROM cards
                 WHERE card_id = ?
                   AND user_id = ?
                   AND card_type = 'CREDIT'
                   AND status = 'ACTIVE'
                """,
                (row, index) -> new CreditCardDetails(
                        row.getLong("card_id"),
                        row.getString("last_four"),
                        row.getBigDecimal("credit_limit"),
                        row.getBigDecimal("outstanding_amount"),
                        row.getBigDecimal("minimum_due_amount")
                ),
                cardId,
                userId
        ).stream().findFirst();
    }

    public Long createOrder(
            Long userId,
            Long cardId,
            Long billerId,
            String consumerReference,
            BigDecimal amount,
            String referenceNumber,
            String otpHash,
            LocalDateTime otpExpiry) {

        String idempotencyKey = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO credit_card_bill_payment_orders (
                    card_id,
                    biller_id,
                    consumer_reference,
                    amount,
                    reference_number,
                    idempotency_key,
                    status,
                    otp_hash,
                    otp_expiry,
                    requested_at
                )
                VALUES (?, ?, ?, ?, ?, ?, 'OTP_PENDING', ?, ?, SYSTIMESTAMP)
                """,
                cardId,
                billerId,
                consumerReference,
                amount,
                referenceNumber,
                idempotencyKey,
                otpHash,
                Timestamp.valueOf(otpExpiry)
        );

        return jdbcTemplate.queryForObject("""
                SELECT credit_card_bill_payment_order_id
                  FROM credit_card_bill_payment_orders
                 WHERE idempotency_key = ?
                """,
                Long.class,
                idempotencyKey
        );
    }

    public Optional<BillPaymentOrder> findOrder(
            Long paymentOrderId,
            Long userId) {

        return jdbcTemplate.query("""
                SELECT p.credit_card_bill_payment_order_id,
                       p.card_id,
                       p.biller_id,
                       p.consumer_reference,
                       p.amount,
                       p.reference_number,
                       p.status,
                       p.otp_hash,
                       p.otp_expiry,
                       p.requested_at,
                       p.completed_at
                  FROM credit_card_bill_payment_orders p
                 WHERE p.credit_card_bill_payment_order_id = ?
                   AND p.card_id IN (
                       SELECT c.card_id
                         FROM cards c
                        WHERE c.user_id = ?
                   )
                """,
                (row, index) -> new BillPaymentOrder(
                        row.getLong("credit_card_bill_payment_order_id"),
                        row.getLong("card_id"),
                        row.getLong("biller_id"),
                        row.getString("consumer_reference"),
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
            Long paymentOrderId,
            Long userId,
            String otpHash,
            LocalDateTime otpExpiry) {

        int changed = jdbcTemplate.update("""
                UPDATE credit_card_bill_payment_orders
                   SET otp_hash = ?,
                       otp_expiry = ?
                 WHERE credit_card_bill_payment_order_id = ?
                   AND status = 'OTP_PENDING'
                   AND card_id IN (
                       SELECT card_id
                         FROM cards
                        WHERE user_id = ?
                   )
                """,
                otpHash,
                Timestamp.valueOf(otpExpiry),
                paymentOrderId,
                userId
        );

        if (changed != 1) {
            throw new IllegalStateException(
                    "Bill-payment order is no longer awaiting OTP."
            );
        }
    }

    public void markExpired(
            Long paymentOrderId,
            Long userId) {

        jdbcTemplate.update("""
                UPDATE credit_card_bill_payment_orders
                   SET status = 'EXPIRED',
                       otp_hash = NULL,
                       otp_expiry = NULL
                 WHERE credit_card_bill_payment_order_id = ?
                   AND status = 'OTP_PENDING'
                   AND card_id IN (
                       SELECT card_id
                         FROM cards
                        WHERE user_id = ?
                   )
                """,
                paymentOrderId,
                userId
        );
    }

    public boolean increaseOutstandingBalance(
            Long cardId,
            Long userId,
            BigDecimal amount) {

        return jdbcTemplate.update("""
                UPDATE cards
                   SET outstanding_amount = outstanding_amount + ?,
                       minimum_due_amount = GREATEST(
                           minimum_due_amount,
                           ROUND((outstanding_amount + ?) * 0.05, 2)
                       )
                 WHERE card_id = ?
                   AND user_id = ?
                   AND card_type = 'CREDIT'
                   AND status = 'ACTIVE'
                   AND credit_limit - outstanding_amount >= ?
                """,
                amount,
                amount,
                cardId,
                userId,
                amount
        ) == 1;
    }

    public boolean completeOrder(
            Long paymentOrderId,
            Long userId) {

        return jdbcTemplate.update("""
                UPDATE credit_card_bill_payment_orders
                   SET status = 'SUCCESS',
                       otp_hash = NULL,
                       otp_expiry = NULL,
                       completed_at = SYSTIMESTAMP
                 WHERE credit_card_bill_payment_order_id = ?
                   AND status = 'OTP_PENDING'
                   AND card_id IN (
                       SELECT card_id
                         FROM cards
                        WHERE user_id = ?
                   )
                """,
                paymentOrderId,
                userId
        ) == 1;
    }

    public void createBillPayment(
            Long paymentOrderId,
            Long cardId,
            Long billerId,
            String consumerReference,
            BigDecimal amount,
            BigDecimal outstandingBefore,
            BigDecimal outstandingAfter,
            String referenceNumber) {

        jdbcTemplate.update("""
                INSERT INTO credit_card_bill_payments (
                    credit_card_bill_payment_order_id,
                    card_id,
                    biller_id,
                    consumer_reference,
                    amount,
                    outstanding_before,
                    outstanding_after,
                    reference_number,
                    payment_date
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP)
                """,
                paymentOrderId,
                cardId,
                billerId,
                consumerReference,
                amount,
                outstandingBefore,
                outstandingAfter,
                referenceNumber
        );
    }

    public record CreditCardDetails(
            Long cardId,
            String lastFour,
            BigDecimal creditLimit,
            BigDecimal outstandingAmount,
            BigDecimal minimumDueAmount) {
    }

    public record BillPaymentOrder(
            Long paymentOrderId,
            Long cardId,
            Long billerId,
            String consumerReference,
            BigDecimal amount,
            String referenceNumber,
            String status,
            String otpHash,
            LocalDateTime otpExpiry,
            LocalDateTime requestedAt,
            LocalDateTime completedAt) {
    }
}