package com.example.inetbank.forex.rates;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ForexRateRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexRateRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ForexRateResponse> findActiveRates() {

        return jdbcTemplate.query("""
                SELECT currency_code,
                       current_rate_inr,
                       markup_bps,
                       status,
                       reference_source,
                       reference_as_of,
                       updated_at
                  FROM forex_rates
                 WHERE status = 'ACTIVE'
                 ORDER BY currency_code
                """,
                (row, index) -> new ForexRateResponse(
                        row.getString("currency_code"),
                        row.getBigDecimal("current_rate_inr"),
                        row.getInt("markup_bps"),
                        row.getString("status"),
                        row.getString("reference_source"),
                        row.getDate("reference_as_of") == null
                                ? null
                                : row.getDate("reference_as_of")
                                        .toLocalDate(),
                        row.getTimestamp("updated_at")
                                .toLocalDateTime()
                )
        );
    }
}