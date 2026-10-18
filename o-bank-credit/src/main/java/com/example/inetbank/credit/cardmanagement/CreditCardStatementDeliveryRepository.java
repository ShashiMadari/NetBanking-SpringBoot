package com.example.inetbank.credit.cardmanagement;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreditCardStatementDeliveryRepository {

    private final JdbcTemplate jdbcTemplate;

    public CreditCardStatementDeliveryRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StatementDeliveryCandidate> findUndeliveredCards(
            int year,
            int month) {

        return jdbcTemplate.query("""
                SELECT c.card_id,
                       c.user_id
                  FROM cards c
                 WHERE c.card_type = 'CREDIT'
                   AND c.status IN ('ACTIVE', 'BLOCKED')
                   AND NOT EXISTS (
                       SELECT 1
                         FROM credit_card_statement_delivery_log l
                        WHERE l.card_id = c.card_id
                          AND l.statement_year = ?
                          AND l.statement_month = ?
                          AND l.delivery_channel = 'EMAIL'
                   )
                 ORDER BY c.card_id
                """,
                (row, index) -> new StatementDeliveryCandidate(
                        row.getLong("card_id"),
                        row.getLong("user_id")
                ),
                year,
                month
        );
    }

    public boolean recordEmailDelivery(
            Long cardId,
            int year,
            int month) {

        try {
            return jdbcTemplate.update("""
                    INSERT INTO credit_card_statement_delivery_log (
                        card_id,
                        statement_year,
                        statement_month,
                        delivery_channel,
                        delivered_at
                    )
                    VALUES (?, ?, ?, 'EMAIL', SYSTIMESTAMP)
                    """,
                    cardId,
                    year,
                    month
            ) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public record StatementDeliveryCandidate(
            Long cardId,
            Long userId
    ) {
    }
}