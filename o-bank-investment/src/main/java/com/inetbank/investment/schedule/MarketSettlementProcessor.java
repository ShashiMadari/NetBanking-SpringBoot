package com.inetbank.investment.schedule;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.inetbank.investment.InvestmentNotificationService;

@Component
public class MarketSettlementProcessor {

    private static final Logger LOG =
            LoggerFactory.getLogger(MarketSettlementProcessor.class);

    private final JdbcTemplate jdbcTemplate;
    private final InvestmentNotificationService notifications;
    private final boolean enabled;
    private final long settlementDelayMs;

    public MarketSettlementProcessor(
            JdbcTemplate jdbcTemplate,
            InvestmentNotificationService notifications,
            @Value("${app.investment.market-settlement.enabled:true}")
            boolean enabled,
            @Value("${app.investment.market-settlement.delay-ms:60000}")
            long settlementDelayMs) {

        this.jdbcTemplate = jdbcTemplate;
        this.notifications = notifications;
        this.enabled = enabled;
        this.settlementDelayMs = settlementDelayMs;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.investment.market-settlement.fixed-delay-ms:15000}"
    )
    @Transactional
    public void settleDueMarketOrders() {

        if (!enabled) {
            return;
        }

        long delaySeconds = Math.max(1, settlementDelayMs / 1000);

        List<PendingMarketOrder> orders = jdbcTemplate.query(
                """
                SELECT o.order_id,
                       o.user_id,
                       o.reference_number
                  FROM investment_orders o
                  JOIN investment_products p
                    ON p.product_id = o.product_id
                 WHERE o.status = 'SETTLEMENT_PENDING'
                   AND p.product_type IN ('EQUITY', 'ETF')
                   AND COALESCE(
                       o.allotted_at,
                       o.authorised_at,
                       o.updated_at
                   ) <= CAST(SYSTIMESTAMP AS TIMESTAMP)
                         - NUMTODSINTERVAL(?, 'SECOND')
                 ORDER BY COALESCE(
                     o.allotted_at,
                     o.authorised_at,
                     o.updated_at
                 ),
                 o.order_id
                 FETCH FIRST 50 ROWS ONLY
                """,
                (row, index) -> new PendingMarketOrder(
                        row.getLong("order_id"),
                        row.getLong("user_id"),
                        row.getString("reference_number")
                ),
                delaySeconds
        );

        if (!orders.isEmpty()) {
            LOG.info(
                    "Automatically settling {} market investment order(s).",
                    orders.size()
            );
        }

        for (PendingMarketOrder order : orders) {
            settle(order);
        }
    }

    private void settle(PendingMarketOrder order) {

        int orderUpdated = jdbcTemplate.update(
                """
                UPDATE investment_orders
                   SET status = 'ALLOTTED',
                       allotted_at = COALESCE(allotted_at, SYSTIMESTAMP),
                       updated_at = SYSTIMESTAMP
                 WHERE order_id = ?
                   AND status = 'SETTLEMENT_PENDING'
                """,
                order.orderId()
        );

        if (orderUpdated == 0) {
            return;
        }

        jdbcTemplate.update(
                """
                UPDATE investment_holdings
                   SET status = 'ACTIVE',
                       updated_at = SYSTIMESTAMP
                 WHERE source_order_id = ?
                   AND status = 'SETTLEMENT_PENDING'
                """,
                order.orderId()
        );

        notifications.afterCommit(
                order.userId(),
                "PAYMENT",
                "Your market investment order was settled automatically. "
                        + "Order: " + order.orderId()
                        + ". Reference: " + order.referenceNumber()
                        + ". Status: ALLOTTED."
        );

        LOG.info(
                "Market investment order settled automatically. orderId={}",
                order.orderId()
        );
    }

    private record PendingMarketOrder(
            long orderId,
            long userId,
            String referenceNumber
    ) {
    }
}

