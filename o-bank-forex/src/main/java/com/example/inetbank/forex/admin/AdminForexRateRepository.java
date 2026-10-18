package com.example.inetbank.forex.admin;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminForexRateRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminForexRateRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminForexRateResponse> findAll() {

        return jdbcTemplate.query("""
                SELECT currency_code,
                       base_rate_inr,
                       current_rate_inr,
                       markup_bps,
                       status,
                       reference_source,
                       reference_as_of,
                       updated_at
                  FROM forex_rates
                 ORDER BY currency_code
                """,
                (row, index) -> map(row)
        );
    }

    public boolean update(
            String currencyCode,
            BigDecimal baseRateInr,
            Integer markupBps,
            boolean active) {

        return jdbcTemplate.update("""
                UPDATE forex_rates
                   SET base_rate_inr = ?,
                       current_rate_inr = ?,
                       markup_bps = ?,
                       status = ?,
                       reference_source = 'ADMIN_RATE_EDIT',
                       reference_as_of = TRUNC(SYSDATE),
                       updated_at = SYSTIMESTAMP
                 WHERE currency_code = ?
                """,
                baseRateInr,
                baseRateInr,
                markupBps,
                active ? "ACTIVE" : "INACTIVE",
                currencyCode
        ) == 1;
    }

    public void createHistory(
            String currencyCode,
            BigDecimal previousRateInr,
            BigDecimal newRateInr) {

        jdbcTemplate.update("""
                INSERT INTO forex_rate_history (
                    currency_code,
                    previous_rate_inr,
                    new_rate_inr,
                    change_reason,
                    reference_source,
                    created_at
                )
                VALUES (
                    ?, ?, ?,
                    'ADMIN_RATE_EDIT',
                    'ADMIN_RATE_EDIT',
                    SYSTIMESTAMP
                )
                """,
                currencyCode,
                previousRateInr,
                newRateInr
        );
    }

    public AdminForexRateResponse findByCurrencyCode(
            String currencyCode) {

        return jdbcTemplate.query("""
                SELECT currency_code,
                       base_rate_inr,
                       current_rate_inr,
                       markup_bps,
                       status,
                       reference_source,
                       reference_as_of,
                       updated_at
                  FROM forex_rates
                 WHERE currency_code = ?
                """,
                (row, index) -> map(row),
                currencyCode
        ).stream().findFirst().orElse(null);
    }

    private AdminForexRateResponse map(
            java.sql.ResultSet row)
            throws java.sql.SQLException {

        Date referenceDate = row.getDate("reference_as_of");

        return new AdminForexRateResponse(
                row.getString("currency_code"),
                row.getBigDecimal("base_rate_inr"),
                row.getBigDecimal("current_rate_inr"),
                row.getInt("markup_bps"),
                row.getString("status"),
                row.getString("reference_source"),
                referenceDate == null
                        ? null
                        : referenceDate.toLocalDate(),
                row.getTimestamp("updated_at")
                        .toLocalDateTime()
        );
    }
}