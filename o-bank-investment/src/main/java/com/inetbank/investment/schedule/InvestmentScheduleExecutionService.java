package com.inetbank.investment.schedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.inetbank.investment.kafka.InvestmentOrderDebitQueueService;

@Service
public class InvestmentScheduleExecutionService {

    private static final DateTimeFormatter REFERENCE_DATE =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbcTemplate;
    private final InvestmentOrderDebitQueueService debitQueueService;

    public InvestmentScheduleExecutionService(
            JdbcTemplate jdbcTemplate,
            InvestmentOrderDebitQueueService debitQueueService) {

        this.jdbcTemplate = jdbcTemplate;
        this.debitQueueService = debitQueueService;
    }

    @Transactional
    public void execute(Long scheduleId) {

        Schedule schedule = jdbcTemplate.query("""
                SELECT schedule_id,
                       user_id,
                       source_account_id,
                       product_id,
                       schedule_type,
                       amount,
                       next_debit_date,
                       tenure_months,
                       installments_completed
                  FROM investment_schedules
                 WHERE schedule_id = ?
                   AND status = 'ACTIVE'
                   AND TRUNC(next_debit_date) <= TRUNC(SYSDATE)
                 FOR UPDATE
                """,
                (row, index) -> new Schedule(
                        row.getLong("schedule_id"),
                        row.getLong("user_id"),
                        row.getLong("source_account_id"),
                        row.getLong("product_id"),
                        row.getString("schedule_type"),
                        row.getBigDecimal("amount"),
                        row.getDate("next_debit_date").toLocalDate(),
                        nullableLong(row.getObject("tenure_months")),
                        row.getInt("installments_completed")
                ),
                scheduleId
        ).stream().findFirst().orElse(null);

        if (schedule == null) {
            return;
        }

        /*
         * Safety check for old schedules or a schedule that was manually
         * reactivated after it had reached its tenure.
         */
        if (schedule.tenureMonths() != null
                && schedule.installmentsCompleted()
                >= schedule.tenureMonths()) {

            completeSchedule(schedule.scheduleId());
            return;
        }

        Product product = jdbcTemplate.query("""
                SELECT product_id,
                       product_type,
                       current_price,
                       minimum_amount
                  FROM investment_products
                 WHERE product_id = ?
                   AND active = 'Y'
                """,
                (row, index) -> new Product(
                        row.getLong("product_id"),
                        row.getString("product_type"),
                        row.getBigDecimal("current_price"),
                        row.getBigDecimal("minimum_amount")
                ),
                schedule.productId()
        ).stream().findFirst().orElse(null);

        if (product == null) {
            pauseSchedule(schedule.scheduleId());
            return;
        }

        if (schedule.amount().compareTo(
                product.minimumAmount()
        ) < 0) {
            pauseSchedule(schedule.scheduleId());
            return;
        }

        String investmentMode = "SIP".equals(
                schedule.scheduleType()
        )
                ? "SIP"
                : "AUTO_DEBIT";

        BigDecimal quantity = calculateQuantity(
                product,
                schedule.amount()
        );

        String referenceNumber = "SCH"
                + schedule.scheduleId()
                + schedule.nextDebitDate().format(REFERENCE_DATE);

        Long orderId = createScheduledOrder(
                schedule,
                investmentMode,
                quantity,
                referenceNumber
        );

        debitQueueService.queueScheduledDebit(
                orderId,
                schedule.userId()
        );

        /*
         * The next due date moves now, preventing duplicate scheduler
         * attempts. last_debit_date and installments_completed are updated
         * only later, after Banking confirms successful debit.
         */
        jdbcTemplate.update("""
                UPDATE investment_schedules
                   SET last_order_id = ?,
                       next_debit_date = ADD_MONTHS(
                               next_debit_date,
                               1
                       ),
                       updated_at = SYSTIMESTAMP
                 WHERE schedule_id = ?
                """,
                orderId,
                schedule.scheduleId()
        );
    }

    private Long createScheduledOrder(
            Schedule schedule,
            String investmentMode,
            BigDecimal quantity,
            String referenceNumber) {

        jdbcTemplate.update("""
                INSERT INTO investment_orders (
                    user_id,
                    source_account_id,
                    product_id,
                    schedule_id,
                    order_kind,
                    investment_mode,
                    amount,
                    quantity,
                    status,
                    reference_number,
                    requested_at,
                    updated_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    'BUY',
                    ?,
                    ?,
                    ?,
                    'PENDING_OTP',
                    ?,
                    SYSTIMESTAMP,
                    SYSTIMESTAMP
                )
                """,
                schedule.userId(),
                schedule.sourceAccountId(),
                schedule.productId(),
                schedule.scheduleId(),
                investmentMode,
                schedule.amount(),
                quantity,
                referenceNumber
        );

        return jdbcTemplate.queryForObject("""
                SELECT order_id
                  FROM investment_orders
                 WHERE reference_number = ?
                """,
                Long.class,
                referenceNumber
        );
    }

    private BigDecimal calculateQuantity(
            Product product,
            BigDecimal amount) {

        if ("MUTUAL_FUND".equals(product.productType())
                && product.currentPrice() != null
                && product.currentPrice().compareTo(
                        BigDecimal.ZERO
                ) > 0) {

            return amount.divide(
                    product.currentPrice(),
                    4,
                    RoundingMode.DOWN
            );
        }

        return BigDecimal.ONE;
    }

    private void completeSchedule(Long scheduleId) {

        jdbcTemplate.update("""
                UPDATE investment_schedules
                   SET status = 'COMPLETED',
                       completed_at = SYSTIMESTAMP,
                       updated_at = SYSTIMESTAMP
                 WHERE schedule_id = ?
                """,
                scheduleId
        );
    }

    private void pauseSchedule(Long scheduleId) {

        jdbcTemplate.update("""
                UPDATE investment_schedules
                   SET status = 'PAUSED',
                       updated_at = SYSTIMESTAMP
                 WHERE schedule_id = ?
                """,
                scheduleId
        );
    }

    private static Long nullableLong(Object value) {

        return value instanceof Number number
                ? number.longValue()
                : null;
    }

    private record Schedule(
            Long scheduleId,
            Long userId,
            Long sourceAccountId,
            Long productId,
            String scheduleType,
            BigDecimal amount,
            LocalDate nextDebitDate,
            Long tenureMonths,
            int installmentsCompleted
    ) {
    }

    private record Product(
            Long productId,
            String productType,
            BigDecimal currentPrice,
            BigDecimal minimumAmount
    ) {
    }
}