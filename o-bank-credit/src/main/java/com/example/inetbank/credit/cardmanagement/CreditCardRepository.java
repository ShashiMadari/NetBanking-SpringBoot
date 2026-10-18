package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreditCardRepository {

    private final JdbcTemplate jdbcTemplate;

    public CreditCardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CreditCardResponse> findByUserId(Long userId) {
        return jdbcTemplate.query("""
                SELECT card_id, account_id, last_four, card_type, network,
                       daily_limit, credit_limit, outstanding_amount,
                       minimum_due_amount, payment_due_date, status, created_at
                  FROM cards
                 WHERE user_id = ?
                 ORDER BY created_at DESC
                """,
                (row, index) -> {
                    Date dueDate = row.getDate("payment_due_date");

                    return new CreditCardResponse(
                            row.getLong("card_id"),
                            row.getLong("account_id"),
                            row.getString("last_four"),
                            row.getString("card_type"),
                            row.getString("network"),
                            row.getBigDecimal("daily_limit"),
                            row.getBigDecimal("credit_limit"),
                            row.getBigDecimal("outstanding_amount"),
                            row.getBigDecimal("minimum_due_amount"),
                            dueDate == null ? null : dueDate.toLocalDate(),
                            row.getString("status"),
                            row.getTimestamp("created_at").toLocalDateTime()
                    );
                },
                userId
        );
    }

    public Optional<CardControlDetails> findControlDetails(
            Long cardId,
            Long userId) {

        return jdbcTemplate.query("""
                SELECT card_id, last_four, card_type, status, daily_limit,
                       credit_limit
                  FROM cards
                 WHERE card_id = ?
                   AND user_id = ?
                """,
                (row, index) -> new CardControlDetails(
                        row.getLong("card_id"),
                        row.getString("last_four"),
                        row.getString("card_type"),
                        row.getString("status"),
                        row.getBigDecimal("daily_limit"),
                        row.getBigDecimal("credit_limit")
                ),
                cardId,
                userId
        ).stream().findFirst();
    }

    public boolean updateStatus(
            Long cardId,
            Long userId,
            String expectedStatus,
            String newStatus) {

        return jdbcTemplate.update("""
                UPDATE cards
                   SET status = ?
                 WHERE card_id = ?
                   AND user_id = ?
                   AND status = ?
                """,
                newStatus,
                cardId,
                userId,
                expectedStatus
        ) == 1;
    }

    public boolean updateDailyLimit(
            Long cardId,
            Long userId,
            BigDecimal dailyLimit) {

        return jdbcTemplate.update("""
                UPDATE cards
                   SET daily_limit = ?
                 WHERE card_id = ?
                   AND user_id = ?
                   AND status = 'ACTIVE'
                   AND (card_type <> 'CREDIT' OR ? <= credit_limit)
                """,
                dailyLimit,
                cardId,
                userId,
                dailyLimit
        ) == 1;
    }

    public record CardControlDetails(
            Long cardId,
            String lastFour,
            String cardType,
            String status,
            BigDecimal dailyLimit,
            BigDecimal creditLimit
    ) {
    }
}
