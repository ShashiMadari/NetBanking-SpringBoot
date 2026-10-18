package com.inetbank.investment.schedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InvestmentMarketValuationProcessor {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    InvestmentMarketValuationProcessor.class
            );

    private static final BigDecimal MINIMUM_PRICE =
            new BigDecimal("0.01");

    private static final BigDecimal MAXIMUM_MOVE =
            new BigDecimal("0.0050");

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public InvestmentMarketValuationProcessor(
            JdbcTemplate jdbcTemplate,
            @Value("${app.investment.market.enabled:false}")
            boolean enabled) {

        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.investment.market.fixed-delay-ms:60000}"
    )
    @Transactional
    public void refreshMarketValues() {

        if (!enabled) {
            return;
        }

        List<Map<String, Object>> products =
                jdbcTemplate.queryForList("""
                        SELECT product_id,
                               current_price
                          FROM investment_products
                         WHERE active = 'Y'
                           AND product_type IN (
                               'EQUITY',
                               'ETF',
                               'MUTUAL_FUND'
                           )
                           AND current_price IS NOT NULL
                        """);

        int updatedProducts = 0;

        for (Map<String, Object> product : products) {
            Long productId = number(
                    value(product, "product_id")
            );

            BigDecimal currentPrice = decimal(
                    value(product, "current_price")
            );

            BigDecimal movement = BigDecimal.valueOf(
                    ThreadLocalRandom.current().nextDouble(
                            -MAXIMUM_MOVE.doubleValue(),
                            MAXIMUM_MOVE.doubleValue()
                    )
            );

            BigDecimal latestPrice = currentPrice
                    .multiply(BigDecimal.ONE.add(movement))
                    .max(MINIMUM_PRICE)
                    .setScale(4, RoundingMode.HALF_UP);

            jdbcTemplate.update("""
                    UPDATE investment_products
                       SET current_price = ?
                     WHERE product_id = ?
                    """,
                    latestPrice,
                    productId
            );

            updatedProducts++;
        }

        int updatedHoldings = jdbcTemplate.update("""
                UPDATE investment_holdings h
                   SET current_value = (
                           SELECT ROUND(
                                   h.quantity * p.current_price,
                                   2
                           )
                             FROM investment_products p
                            WHERE p.product_id = h.product_id
                       ),
                       updated_at = SYSTIMESTAMP
                 WHERE h.status = 'ACTIVE'
                   AND h.product_id IN (
                       SELECT product_id
                         FROM investment_products
                        WHERE product_type IN (
                            'EQUITY',
                            'ETF',
                            'MUTUAL_FUND'
                        )
                          AND current_price IS NOT NULL
                   )
                """);

        savePortfolioSnapshots();

        LOG.info(
                "Investment market valuation refreshed. products={}, holdings={}",
                updatedProducts,
                updatedHoldings
        );
    }

    private void savePortfolioSnapshots() {

        jdbcTemplate.update("""
                INSERT INTO investment_portfolio_snapshots (
                    user_id,
                    snapshot_at,
                    invested_amount,
                    current_value,
                    gain_loss
                )
                SELECT h.user_id,
                       SYSTIMESTAMP,
                       ROUND(SUM(h.invested_amount), 2),
                       ROUND(SUM(h.current_value), 2),
                       ROUND(
                           SUM(h.current_value)
                           - SUM(h.invested_amount),
                           2
                       )
                  FROM investment_holdings h
                 WHERE h.status = 'ACTIVE'
                   AND NOT EXISTS (
                       SELECT 1
                         FROM investment_portfolio_snapshots s
                        WHERE s.user_id = h.user_id
                          AND s.snapshot_at >=
                              SYSTIMESTAMP - INTERVAL '1' MINUTE
                   )
                 GROUP BY h.user_id
                """);
    }

    private static Object value(
            Map<String, Object> row,
            String key) {

        if (row.containsKey(key)) {
            return row.get(key);
        }

        return row.get(
                key.toUpperCase(Locale.ROOT)
        );
    }

    private static Long number(Object value) {
        return value instanceof Number number
                ? number.longValue()
                : Long.valueOf(String.valueOf(value));
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal
                ? decimal
                : new BigDecimal(String.valueOf(value));
    }
}