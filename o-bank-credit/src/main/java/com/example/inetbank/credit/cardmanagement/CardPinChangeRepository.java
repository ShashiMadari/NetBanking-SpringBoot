package com.example.inetbank.credit.cardmanagement;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CardPinChangeRepository {

    private final JdbcTemplate jdbcTemplate;

    public CardPinChangeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CardDetails> findActiveCard(
            Long cardId,
            Long userId) {

        return jdbcTemplate.query("""
                SELECT card_id,
                       last_four,
                       card_type
                  FROM cards
                 WHERE card_id = ?
                   AND user_id = ?
                   AND status = 'ACTIVE'
                """,
                (row, index) -> new CardDetails(
                        row.getLong("card_id"),
                        row.getString("last_four"),
                        row.getString("card_type")
                ),
                cardId,
                userId
        ).stream().findFirst();
    }

    public Long createOrder(
            Long cardId,
            Long userId,
            String newPinHash,
            String otpHash,
            LocalDateTime otpExpiry,
            String idempotencyKey) {

        jdbcTemplate.update("""
                INSERT INTO card_pin_change_orders (
                    card_id,
                    user_id,
                    new_pin_hash,
                    otp_hash,
                    otp_expiry,
                    status,
                    idempotency_key,
                    requested_at
                )
                VALUES (?, ?, ?, ?, ?, 'OTP_PENDING', ?, SYSTIMESTAMP)
                """,
                cardId,
                userId,
                newPinHash,
                otpHash,
                Timestamp.valueOf(otpExpiry),
                idempotencyKey
        );

        return jdbcTemplate.queryForObject("""
                SELECT card_pin_change_order_id
                  FROM card_pin_change_orders
                 WHERE idempotency_key = ?
                """,
                Long.class,
                idempotencyKey
        );
    }

    public Optional<PinChangeOrder> findOrder(
            Long orderId,
            Long userId) {

        return jdbcTemplate.query("""
                SELECT card_pin_change_order_id,
                       card_id,
                       user_id,
                       new_pin_hash,
                       otp_hash,
                       otp_expiry,
                       status,
                       requested_at,
                       completed_at
                  FROM card_pin_change_orders
                 WHERE card_pin_change_order_id = ?
                   AND user_id = ?
                """,
                (row, index) -> new PinChangeOrder(
                        row.getLong("card_pin_change_order_id"),
                        row.getLong("card_id"),
                        row.getLong("user_id"),
                        row.getString("new_pin_hash"),
                        row.getString("otp_hash"),
                        row.getTimestamp("otp_expiry")
                                .toLocalDateTime(),
                        row.getString("status"),
                        row.getTimestamp("requested_at")
                                .toLocalDateTime(),
                        row.getTimestamp("completed_at") == null
                                ? null
                                : row.getTimestamp("completed_at")
                                        .toLocalDateTime()
                ),
                orderId,
                userId
        ).stream().findFirst();
    }

    public boolean replaceOtp(
            Long orderId,
            Long userId,
            String otpHash,
            LocalDateTime otpExpiry) {

        return jdbcTemplate.update("""
                UPDATE card_pin_change_orders
                   SET otp_hash = ?,
                       otp_expiry = ?
                 WHERE card_pin_change_order_id = ?
                   AND user_id = ?
                   AND status = 'OTP_PENDING'
                """,
                otpHash,
                Timestamp.valueOf(otpExpiry),
                orderId,
                userId
        ) == 1;
    }

    public void markExpired(
            Long orderId,
            Long userId) {

        jdbcTemplate.update("""
                UPDATE card_pin_change_orders
                SET status = 'EXPIRED'
                WHERE card_pin_change_order_id = ?
                AND user_id = ?
                AND status = 'OTP_PENDING'
                """,
                orderId,
                userId
        );
    }

    public boolean updateCardPin(
            Long cardId,
            Long userId,
            String newPinHash) {

        return jdbcTemplate.update("""
                UPDATE cards
                   SET pin_hash = ?
                 WHERE card_id = ?
                   AND user_id = ?
                   AND status = 'ACTIVE'
                """,
                newPinHash,
                cardId,
                userId
        ) == 1;
    }

    public boolean completeOrder(
            Long orderId,
            Long userId) {

        return jdbcTemplate.update("""
                UPDATE card_pin_change_orders
                SET status = 'SUCCESS',
                    completed_at = SYSTIMESTAMP
                WHERE card_pin_change_order_id = ?
                AND user_id = ?
                AND status = 'OTP_PENDING'
                """,
                orderId,
                userId
        ) == 1;
    }

    public record CardDetails(
            Long cardId,
            String lastFour,
            String cardType
    ) {
    }

    public record PinChangeOrder(
            Long orderId,
            Long cardId,
            Long userId,
            String newPinHash,
            String otpHash,
            LocalDateTime otpExpiry,
            String status,
            LocalDateTime requestedAt,
            LocalDateTime completedAt
    ) {
    }
}