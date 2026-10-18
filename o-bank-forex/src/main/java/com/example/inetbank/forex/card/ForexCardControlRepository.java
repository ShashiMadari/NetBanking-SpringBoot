package com.example.inetbank.forex.card;

import java.math.BigDecimal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ForexCardControlRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexCardControlRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public ForexCardResponse findByIdAndUserId(
            Long cardId,
            Long userId) {

        return jdbcTemplate.query("""
                SELECT forex_card_id,
                       wallet_id,
                       card_number_masked,
                       card_token,
                       status,
                       spend_limit,
                       available_balance,
                       issued_at
                  FROM forex_cards
                 WHERE forex_card_id = ?
                   AND user_id = ?
                """,
                (row, index) -> new ForexCardResponse(
                        row.getLong("forex_card_id"),
                        row.getLong("wallet_id"),
                        row.getString("card_number_masked"),
                        row.getString("card_token"),
                        row.getString("status"),
                        row.getBigDecimal("spend_limit"),
                        row.getBigDecimal("available_balance"),
                        row.getTimestamp("issued_at")
                                .toLocalDateTime()
                ),
                cardId,
                userId
        ).stream().findFirst().orElse(null);
    }

    public boolean updateStatus(
            Long cardId,
            Long userId,
            String status) {

        return jdbcTemplate.update("""
                UPDATE forex_cards
                   SET status = ?,
                       updated_at = SYSTIMESTAMP
                 WHERE forex_card_id = ?
                   AND user_id = ?
                """,
                status,
                cardId,
                userId
        ) == 1;
    }

    public boolean updateSpendLimit(
            Long cardId,
            Long userId,
            BigDecimal amount) {

        return jdbcTemplate.update("""
                UPDATE forex_cards
                   SET spend_limit = ?,
                       updated_at = SYSTIMESTAMP
                 WHERE forex_card_id = ?
                   AND user_id = ?
                """,
                amount,
                cardId,
                userId
        ) == 1;
    }
}