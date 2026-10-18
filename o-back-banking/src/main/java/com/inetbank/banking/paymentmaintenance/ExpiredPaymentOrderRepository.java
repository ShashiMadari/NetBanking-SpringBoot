package com.inetbank.banking.paymentmaintenance;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExpiredPaymentOrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExpiredPaymentOrderRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExpiredPaymentOrder> findExpiredPendingOrders() {

        return jdbcTemplate.query("""
                SELECT p.payment_order_id,
                       a.user_id,
                       p.reference_number
                  FROM payment_orders p
                  JOIN accounts a
                    ON a.account_id = p.source_account_id
                 WHERE p.status = 'OTP_PENDING'
                   AND p.otp_expiry < SYSTIMESTAMP
                 ORDER BY p.payment_order_id
                """,
                (row, index) -> new ExpiredPaymentOrder(
                        row.getLong("payment_order_id"),
                        row.getLong("user_id"),
                        row.getString("reference_number")
                )
        );
    }

    public boolean markExpired(Long paymentOrderId) {

        return jdbcTemplate.update("""
                UPDATE payment_orders
                   SET status = 'FAILED',
                       otp_hash = NULL,
                       otp_expiry = NULL
                 WHERE payment_order_id = ?
                   AND status = 'OTP_PENDING'
                   AND otp_expiry < SYSTIMESTAMP
                """, paymentOrderId) == 1;
    }

    public record ExpiredPaymentOrder(
            Long paymentOrderId,
            Long customerUserId,
            String referenceNumber) {
    }
}