package com.example.inetbank.forex.quote;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ForexQuoteRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexQuoteRepository(
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

    public Optional<ActiveRate> findActiveRate(
            String currencyCode) {

        return jdbcTemplate.query("""
                SELECT current_rate_inr,
                       markup_bps
                  FROM forex_rates
                 WHERE currency_code = ?
                   AND status = 'ACTIVE'
                """,
                (row, index) -> new ActiveRate(
                        row.getBigDecimal("current_rate_inr"),
                        row.getInt("markup_bps")
                ),
                currencyCode
        ).stream().findFirst();
    }

    public void saveQuote(
            String quoteId,
            Long userId,
            String fromCurrency,
            String toCurrency,
            BigDecimal fromAmount,
            BigDecimal receiveAmount,
            BigDecimal customerRateInr,
            Integer markupBps,
            LocalDateTime expiresAt) {

        jdbcTemplate.update("""
                INSERT INTO forex_quotes (
                    quote_id,
                    user_id,
                    from_currency,
                    to_currency,
                    from_amount,
                    receive_amount,
                    customer_rate_inr,
                    markup_bps,
                    status,
                    expires_at,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'LOCKED', ?, SYSTIMESTAMP)
                """,
                quoteId,
                userId,
                fromCurrency,
                toCurrency,
                fromAmount,
                receiveAmount,
                customerRateInr,
                markupBps,
                Timestamp.valueOf(expiresAt)
        );
    }

    public record ActiveRate(
            BigDecimal currentRateInr,
            Integer markupBps
    ) {
    }
}