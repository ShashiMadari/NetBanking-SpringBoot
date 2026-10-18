package com.inetbank.investment.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class InvestmentOrderDebitQueueService {

    private static final String INVESTMENT_ORDER_TOPIC =
            "investment.order.placed.v1";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public InvestmentOrderDebitQueueService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {

        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /*
     * Used after the customer enters a valid OTP for a manual purchase.
     */
    @Transactional
    public String queueDebitAfterOtpVerification(
            Long orderId,
            Long userId) {

        return queueDebit(
                orderId,
                userId
        );
    }

    /*
     * Used by the SIP/RD/PPF schedule processor.
     * The customer already approved the recurring schedule, so no new OTP
     * is required for each monthly debit.
     */
    @Transactional
    public String queueScheduledDebit(
            Long orderId,
            Long userId) {

        return queueDebit(
                orderId,
                userId
        );
    }

    private String queueDebit(
            Long orderId,
            Long userId) {

        OrderForDebit order = jdbcTemplate.query("""
                SELECT order_id,
                       user_id,
                       source_account_id,
                       amount,
                       reference_number,
                       investment_mode,
                       status
                  FROM investment_orders
                 WHERE order_id = ?
                   AND user_id = ?
                   AND order_kind = 'BUY'
                   AND investment_mode IN (
                       'LUMPSUM',
                       'SIP',
                       'AUTO_DEBIT'
                   )
                   AND status = 'PENDING_OTP'
                 FOR UPDATE
                """,
                (row, index) -> new OrderForDebit(
                        row.getLong("order_id"),
                        row.getLong("user_id"),
                        row.getLong("source_account_id"),
                        row.getBigDecimal("amount"),
                        row.getString("reference_number"),
                        row.getString("investment_mode"),
                        row.getString("status")
                ),
                orderId,
                userId
        ).stream().findFirst().orElse(null);

        if (order == null) {
            throw new IllegalStateException(
                    "The investment order is unavailable for debit."
            );
        }

        String eventId = UUID.randomUUID().toString();

        int updated = jdbcTemplate.update("""
                UPDATE investment_orders
                   SET status = 'DEBIT_PENDING',
                       debit_event_id = ?,
                       otp_hash = NULL,
                       otp_expiry = NULL,
                       authorised_at = SYSTIMESTAMP,
                       updated_at = SYSTIMESTAMP
                 WHERE order_id = ?
                   AND status = 'PENDING_OTP'
                """,
                eventId,
                order.orderId()
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "The investment order could not be queued for debit."
            );
        }

        InvestmentOrderPlacedEvent event =
                new InvestmentOrderPlacedEvent(
                        eventId,
                        order.orderId(),
                        order.userId(),
                        order.sourceAccountId(),
                        order.amount(),
                        order.referenceNumber(),
                        "INVESTMENT_PURCHASE",
                        Instant.now()
                );

        String payload = createPayload(event);

        jdbcTemplate.update("""
                INSERT INTO investment_order_outbox (
                    event_id,
                    order_id,
                    event_type,
                    topic_name,
                    event_key,
                    payload,
                    publish_status,
                    publish_attempts,
                    created_at
                )
                VALUES (
                    ?,
                    ?,
                    'INVESTMENT_ORDER_PLACED',
                    ?,
                    ?,
                    ?,
                    'PENDING',
                    0,
                    SYSTIMESTAMP
                )
                """,
                eventId,
                order.orderId(),
                INVESTMENT_ORDER_TOPIC,
                String.valueOf(order.orderId()),
                payload
        );

        return eventId;
    }

    private String createPayload(
            InvestmentOrderPlacedEvent event) {

        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not create the investment debit Kafka event.",
                    exception
            );
        }
    }

    private record OrderForDebit(
            Long orderId,
            Long userId,
            Long sourceAccountId,
            BigDecimal amount,
            String referenceNumber,
            String investmentMode,
            String status
    ) {
    }

    private record InvestmentOrderPlacedEvent(
            String eventId,
            Long orderId,
            Long userId,
            Long accountId,
            BigDecimal amount,
            String referenceNumber,
            String transactionType,
            Instant createdAt
    ) {
    }
}