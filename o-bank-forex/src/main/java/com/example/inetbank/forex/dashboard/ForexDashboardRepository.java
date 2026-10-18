package com.example.inetbank.forex.dashboard;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;
import com.example.inetbank.forex.wallet.ForexWalletResponse;

@Repository
public class ForexDashboardRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexDashboardRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ForexWalletResponse> findWallets(
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

    public List<ForexTransactionResponse> findRecentTransactions(
            Long userId) {

        return jdbcTemplate.query("""
                SELECT forex_transaction_id,
                       transaction_type,
                       from_currency,
                       to_currency,
                       from_amount,
                       receive_amount,
                       customer_rate_inr,
                       inr_equivalent,
                       payment_method,
                       status,
                       reference_number,
                       created_at
                  FROM forex_transactions
                 WHERE user_id = ?
                 ORDER BY created_at DESC,
                          forex_transaction_id DESC
                 FETCH FIRST 10 ROWS ONLY
                """,
                (row, index) -> new ForexTransactionResponse(
                        row.getLong("forex_transaction_id"),
                        row.getString("transaction_type"),
                        row.getString("from_currency"),
                        row.getString("to_currency"),
                        row.getBigDecimal("from_amount"),
                        row.getBigDecimal("receive_amount"),
                        row.getBigDecimal("customer_rate_inr"),
                        row.getBigDecimal("inr_equivalent"),
                        row.getString("payment_method"),
                        row.getString("status"),
                        row.getString("reference_number"),
                        row.getTimestamp("created_at")
                                .toLocalDateTime()
                ),
                userId
        );
    }
}