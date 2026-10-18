package com.example.inetbank.forex.wallet;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ForexWalletRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexWalletRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ForexWalletResponse> findByUserId(
            Long userId) {

        return jdbcTemplate.query("""
                SELECT wallet_id,
                       currency_code,
                       available_balance,
                       updated_at
                  FROM forex_wallets
                 WHERE user_id = ?
                 ORDER BY currency_code
                """,
                (row, index) -> new ForexWalletResponse(
                        row.getLong("wallet_id"),
                        row.getString("currency_code"),
                        row.getBigDecimal("available_balance"),
                        row.getTimestamp("updated_at")
                                .toLocalDateTime()
                ),
                userId
        );
    }
}