package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoanPaymentOrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoanPaymentOrderRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public Long create(
            Long sourceAccountId,
            Long loanId,
            String paymentType,
            BigDecimal amount,
            String referenceNumber,
            String idempotencyKey,
            String otpHash,
            LocalDateTime otpExpiry) {

        jdbcTemplate.update("""
                INSERT INTO loan_payment_orders (
                    source_account_id,
                    loan_id,
                    payment_type,
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
                sourceAccountId,
                loanId,
                paymentType,
                amount,
                referenceNumber,
                idempotencyKey,
                otpHash,
                Timestamp.valueOf(otpExpiry)
        );

        return jdbcTemplate.queryForObject("""
                SELECT loan_payment_order_id
                  FROM loan_payment_orders
                 WHERE idempotency_key = ?
                """,
                Long.class,
                idempotencyKey
        );
    }

    public Optional<LoanPaymentOrderDetails> findById(
            Long paymentOrderId) {

        return jdbcTemplate.query("""
                SELECT p.loan_payment_order_id,
                       p.source_account_id,
                       p.loan_id,
                       l.user_id AS customer_user_id,
                       l.loan_account_number,
                       p.payment_type,
                       p.amount,
                       p.reference_number,
                       p.status,
                       p.otp_hash,
                       p.otp_expiry,
                       p.requested_at,
                       p.completed_at
                  FROM loan_payment_orders p
                  JOIN loans l
                    ON l.loan_id = p.loan_id
                 WHERE p.loan_payment_order_id = ?
                """,
                (row, index) -> map(row),
                paymentOrderId
        ).stream().findFirst();
    }

    public boolean replaceOtp(
            Long paymentOrderId,
            String otpHash,
            LocalDateTime otpExpiry) {

        return jdbcTemplate.update("""
                UPDATE loan_payment_orders
                   SET otp_hash = ?,
                       otp_expiry = ?
                 WHERE loan_payment_order_id = ?
                   AND status = 'OTP_PENDING'
                """,
                otpHash,
                Timestamp.valueOf(otpExpiry),
                paymentOrderId
        ) == 1;
    }

    public boolean markSuccess(Long paymentOrderId) {
        return jdbcTemplate.update("""
                UPDATE loan_payment_orders
                   SET status = 'SUCCESS',
                       otp_hash = NULL,
                       otp_expiry = NULL,
                       completed_at = SYSTIMESTAMP
                 WHERE loan_payment_order_id = ?
                   AND status = 'OTP_PENDING'
                """,
                paymentOrderId
        ) == 1;
    }

    public boolean markFailed(Long paymentOrderId) {
        return jdbcTemplate.update("""
                UPDATE loan_payment_orders
                   SET status = 'FAILED',
                       otp_hash = NULL,
                       otp_expiry = NULL
                 WHERE loan_payment_order_id = ?
                   AND status = 'OTP_PENDING'
                """,
                paymentOrderId
        ) == 1;
    }

    public boolean markExpired(Long paymentOrderId) {
        return jdbcTemplate.update("""
                UPDATE loan_payment_orders
                SET status = 'EXPIRED',
                    otp_hash = NULL,
                    otp_expiry = NULL
                WHERE loan_payment_order_id = ?
                AND status = 'OTP_PENDING'
                """,
                paymentOrderId
        ) == 1;
    }

    private LoanPaymentOrderDetails map(
            java.sql.ResultSet row) throws java.sql.SQLException {

        Timestamp otpExpiry = row.getTimestamp("otp_expiry");
        Timestamp requestedAt = row.getTimestamp("requested_at");
        Timestamp completedAt = row.getTimestamp("completed_at");

        return new LoanPaymentOrderDetails(
                row.getLong("loan_payment_order_id"),
                row.getLong("source_account_id"),
                row.getLong("loan_id"),
                row.getLong("customer_user_id"),
                row.getString("loan_account_number"),
                row.getString("payment_type"),
                row.getBigDecimal("amount"),
                row.getString("reference_number"),
                row.getString("status"),
                row.getString("otp_hash"),
                otpExpiry == null ? null : otpExpiry.toLocalDateTime(),
                requestedAt.toLocalDateTime(),
                completedAt == null
                        ? null
                        : completedAt.toLocalDateTime()
        );
    }

    

    public record LoanPaymentOrderDetails(
            Long paymentOrderId,
            Long sourceAccountId,
            Long loanId,
            Long customerUserId,
            String loanAccountNumber,
            String paymentType,
            BigDecimal amount,
            String referenceNumber,
            String status,
            String otpHash,
            LocalDateTime otpExpiry,
            LocalDateTime requestedAt,
            LocalDateTime completedAt) {
    }
}