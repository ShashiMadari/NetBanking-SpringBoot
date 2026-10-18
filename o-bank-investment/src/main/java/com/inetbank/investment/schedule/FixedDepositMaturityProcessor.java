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
public class FixedDepositMaturityProcessor {

    private static final Logger LOG =
            LoggerFactory.getLogger(FixedDepositMaturityProcessor.class);

    private final JdbcTemplate jdbc;
    private final InvestmentNotificationService notifications;
    private final boolean enabled;

    public FixedDepositMaturityProcessor(
            JdbcTemplate jdbc,
            InvestmentNotificationService notifications,
            @Value("${app.investment.fd-maturity.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString =
            "${app.investment.fd-maturity.fixed-delay-ms:3600000}")
    @Transactional
    public void markDueDepositsMatured() {
        if (!enabled) return;

        List<DueDeposit> due = jdbc.query("""
                SELECT h.holding_id, h.user_id, h.tenure_months
                  FROM investment_holdings h
                  JOIN investment_products p ON p.product_id = h.product_id
                 WHERE p.product_type = 'FD'
                   AND h.status = 'ACTIVE'
                   AND h.tenure_months IS NOT NULL
                   AND ADD_MONTHS(CAST(h.opened_at AS DATE), h.tenure_months) <= SYSDATE
                 ORDER BY h.opened_at, h.holding_id
                 FETCH FIRST 100 ROWS ONLY
                """, (row, number) -> new DueDeposit(
                row.getLong("holding_id"),
                row.getLong("user_id"),
                row.getInt("tenure_months")));

        for (DueDeposit deposit : due) {
            int updated = jdbc.update("""
                    UPDATE investment_holdings
                       SET status = 'MATURED', updated_at = SYSTIMESTAMP
                     WHERE holding_id = ? AND status = 'ACTIVE'
                    """, deposit.holdingId());
            if (updated == 1) {
                notifications.afterCommit(deposit.userId(), "PAYMENT",
                        "Your Fixed Deposit has reached its " + deposit.tenureMonths()
                        + "-month maturity date. It is awaiting manual settlement. "
                        + "Holding: " + deposit.holdingId() + ".");
                LOG.info("FD matured and queued for manual settlement. holdingId={}",
                        deposit.holdingId());
            }
        }
    }

    private record DueDeposit(long holdingId, long userId, int tenureMonths) { }
}
