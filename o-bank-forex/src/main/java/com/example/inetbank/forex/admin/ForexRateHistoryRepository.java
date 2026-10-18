package com.example.inetbank.forex.admin;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ForexRateHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexRateHistoryRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ForexRateHistoryResponse> findByCurrencyCode(
            String currencyCode) {

        return jdbcTemplate.query("""
                SELECT rate_history_id,
                       currency_code,
                       previous_rate_inr,
                       new_rate_inr,
                       change_reason,
                       reference_source,
                       created_at
                  FROM forex_rate_history
                 WHERE currency_code = ?
                 ORDER BY created_at DESC,
                          rate_history_id DESC
                 FETCH FIRST 500 ROWS ONLY
                """,
                (row, index) -> new ForexRateHistoryResponse(
                        row.getLong("rate_history_id"),
                        row.getString("currency_code"),
                        row.getBigDecimal("previous_rate_inr"),
                        row.getBigDecimal("new_rate_inr"),
                        row.getString("change_reason"),
                        row.getString("reference_source"),
                        row.getTimestamp("created_at")
                                .toLocalDateTime()
                ),
                currencyCode
        );
    }
}