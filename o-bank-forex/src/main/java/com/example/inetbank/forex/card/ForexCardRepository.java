package com.example.inetbank.forex.card;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ForexCardRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexCardRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isKycApproved(Long userId) {
        return Boolean.TRUE.equals(
                jdbcTemplate.query("""
                        SELECT kyc_status
                          FROM user_kyc
                         WHERE user_id = ?
                        """,
                        (row, index) -> "APPROVED".equals(
                                row.getString("kyc_status")
                        ),
                        userId
                ).stream().findFirst().orElse(false)
        );
    }

    public Long findWalletId(
            Long userId,
            String currencyCode) {

        return jdbcTemplate.query("""
                SELECT wallet_id
                  FROM forex_wallets
                 WHERE user_id = ?
                   AND currency_code = ?
                """,
                (row, index) -> row.getLong("wallet_id"),
                userId,
                currencyCode
        ).stream().findFirst().orElse(null);
    }

    public boolean hasActiveRate(String currencyCode) {
        return Boolean.TRUE.equals(
                jdbcTemplate.query("""
                        SELECT 1
                          FROM forex_rates
                         WHERE currency_code = ?
                           AND status = 'ACTIVE'
                        """,
                        (row, index) -> true,
                        currencyCode
                ).stream().findFirst().orElse(false)
        );
    }

    public Long create(
            Long userId,
            Long walletId,
            String maskedNumber,
            String cardToken,
            java.math.BigDecimal spendLimit) {

        jdbcTemplate.update("""
                INSERT INTO forex_cards (
                    user_id,
                    wallet_id,
                    card_number_masked,
                    card_token,
                    status,
                    spend_limit,
                    available_balance,
                    issued_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, 'ACTIVE', ?, 0,
                    SYSTIMESTAMP, SYSTIMESTAMP
                )
                """,
                userId,
                walletId,
                maskedNumber,
                cardToken,
                spendLimit
        );

        return jdbcTemplate.queryForObject("""
                SELECT forex_card_id
                  FROM forex_cards
                 WHERE card_token = ?
                """,
                Long.class,
                cardToken
        );
    }

    public ForexCardResponse findByIdAndUserId(
            Long cardId,
            Long userId) {

        return jdbcTemplate.queryForObject("""
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
                (row, index) -> map(row),
                cardId,
                userId
        );
    }

    public List<ForexCardResponse> findByUserId(
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
                 WHERE user_id = ?
                 ORDER BY forex_card_id DESC
                """,
                (row, index) -> map(row),
                userId
        );
    }

    private ForexCardResponse map(
            java.sql.ResultSet row)
            throws java.sql.SQLException {

        return new ForexCardResponse(
                row.getLong("forex_card_id"),
                row.getLong("wallet_id"),
                row.getString("card_number_masked"),
                row.getString("card_token"),
                row.getString("status"),
                row.getBigDecimal("spend_limit"),
                row.getBigDecimal("available_balance"),
                row.getTimestamp("issued_at")
                        .toLocalDateTime()
        );
    }
}