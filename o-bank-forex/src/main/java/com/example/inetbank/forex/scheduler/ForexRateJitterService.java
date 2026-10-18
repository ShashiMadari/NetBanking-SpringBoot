package com.example.inetbank.forex.scheduler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForexRateJitterService {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom random = new SecureRandom();
    private final BigDecimal minStepPercent;
    private final BigDecimal maxStepPercent;
    private final BigDecimal maxDeviationPercent;

    public ForexRateJitterService(
            JdbcTemplate jdbcTemplate,
            @Value("${app.forex.rate-min-step-percent:0.025}")
            BigDecimal minStepPercent,
            @Value("${app.forex.rate-max-step-percent:0.040}")
            BigDecimal maxStepPercent,
            @Value("${app.forex.rate-max-deviation-percent:1.50}")
            BigDecimal maxDeviationPercent) {

        this.jdbcTemplate = jdbcTemplate;
        this.minStepPercent = minStepPercent;
        this.maxStepPercent = maxStepPercent;
        this.maxDeviationPercent = maxDeviationPercent;
    }

    @Transactional
    public void updateRates() {

        List<Rate> rates = jdbcTemplate.query("""
                SELECT currency_code,
                       base_rate_inr,
                       current_rate_inr
                  FROM forex_rates
                 WHERE status = 'ACTIVE'
                """,
                (row, index) -> new Rate(
                        row.getString("currency_code"),
                        row.getBigDecimal("base_rate_inr"),
                        row.getBigDecimal("current_rate_inr")
                )
        );

        for (Rate rate : rates) {
            BigDecimal updatedRate = nextRate(rate);

            jdbcTemplate.update("""
                    UPDATE forex_rates
                       SET current_rate_inr = ?,
                           reference_source = 'SCHEDULED_SIMULATION',
                           reference_as_of = TRUNC(SYSDATE),
                           updated_at = SYSTIMESTAMP
                     WHERE currency_code = ?
                    """,
                    updatedRate,
                    rate.currencyCode()
            );

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
                        ?,
                        ?,
                        ?,
                        'SCHEDULED_SIMULATION',
                        'SCHEDULED_SIMULATION',
                        SYSTIMESTAMP
                    )
                    """,
                    rate.currencyCode(),
                    rate.currentRateInr(),
                    updatedRate
            );
        }
    }

    private BigDecimal nextRate(Rate rate) {

        BigDecimal minimum = rate.baseRateInr().multiply(
                ONE.subtract(
                        maxDeviationPercent.divide(
                                HUNDRED,
                                12,
                                RoundingMode.HALF_UP
                        )
                )
        );

        BigDecimal maximum = rate.baseRateInr().multiply(
                ONE.add(
                        maxDeviationPercent.divide(
                                HUNDRED,
                                12,
                                RoundingMode.HALF_UP
                        )
                )
        );

        BigDecimal stepPercent = minStepPercent.add(
                maxStepPercent.subtract(minStepPercent)
                        .multiply(BigDecimal.valueOf(
                                random.nextDouble()
                        ))
        );

        BigDecimal stepFraction = stepPercent.divide(
                HUNDRED,
                12,
                RoundingMode.HALF_UP
        );

        int direction = chooseDirection(rate);

        BigDecimal candidate = rate.currentRateInr().multiply(
                ONE.add(
                        stepFraction.multiply(
                                BigDecimal.valueOf(direction)
                        )
                )
        );

        if (candidate.compareTo(minimum) < 0) {
            candidate = minimum;
        }

        if (candidate.compareTo(maximum) > 0) {
            candidate = maximum;
        }

        return candidate.setScale(
                6,
                RoundingMode.HALF_UP
        );
    }

    private int chooseDirection(Rate rate) {

        int position = rate.currentRateInr().compareTo(
                rate.baseRateInr()
        );

        if (position == 0) {
            return random.nextBoolean() ? 1 : -1;
        }

        boolean moveTowardBase = random.nextDouble() < 0.70;

        if (moveTowardBase) {
            return position > 0 ? -1 : 1;
        }

        return position > 0 ? 1 : -1;
    }

    private record Rate(
            String currencyCode,
            BigDecimal baseRateInr,
            BigDecimal currentRateInr
    ) {
    }
}