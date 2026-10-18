package com.inetbank.investment.schedule;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class InvestmentScheduleProcessor {

    private final JdbcTemplate jdbcTemplate;
    private final InvestmentScheduleExecutionService executionService;
    private final boolean enabled;
    private static final Logger LOG =
        LoggerFactory.getLogger(
                InvestmentScheduleProcessor.class
        );

    public InvestmentScheduleProcessor(
            JdbcTemplate jdbcTemplate,
            InvestmentScheduleExecutionService executionService,
            @Value("${app.investment.schedule.enabled:false}")
            boolean enabled) {

        this.jdbcTemplate = jdbcTemplate;
        this.executionService = executionService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.investment.schedule.fixed-delay-ms:60000}"
    )
    public void processDueSchedules() {

        if (!enabled) {
            return;
        }

        List<Long> dueScheduleIds = jdbcTemplate.query("""
                SELECT schedule_id
                  FROM investment_schedules
                 WHERE status = 'ACTIVE'
                   AND TRUNC(next_debit_date) <= TRUNC(SYSDATE)
                 ORDER BY next_debit_date,
                          schedule_id
                FETCH FIRST 50 ROWS ONLY
                """,
                (row, index) -> row.getLong("schedule_id")
        );

        LOG.info(
        "Checking due investment schedules. count={}",
        dueScheduleIds.size()
);

        for (Long scheduleId : dueScheduleIds) {
            try {
                executionService.execute(scheduleId);
            } catch (Exception exception) {
            LOG.error(
                    "Scheduled investment debit failed. scheduleId={}",
                    scheduleId,
                    exception
            );
        }
        }
    }
}