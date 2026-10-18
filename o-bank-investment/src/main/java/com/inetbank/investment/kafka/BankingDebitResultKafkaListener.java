package com.inetbank.investment.kafka;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inetbank.investment.InvestmentNotificationService;
import com.inetbank.investment.kafka.event.BankingDebitFailedEvent;
import com.inetbank.investment.kafka.event.BankingDebitSucceededEvent;

@Component
public class BankingDebitResultKafkaListener {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // NEW: sends customer notifications through Identity.
    private final InvestmentNotificationService investmentNotifications;

    public BankingDebitResultKafkaListener(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            InvestmentNotificationService investmentNotifications) {

        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.investmentNotifications = investmentNotifications;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.banking-debit-succeeded}"
    )
    @Transactional
    public void handleDebitSucceeded(String payload) {

        BankingDebitSucceededEvent event = readSuccessEvent(payload);

        // Existing duplicate-event protection.
        if (alreadyProcessed(event.eventId())) {
            return;
        }

        InvestmentOrder order = findPendingOrder(
                event.orderId(),
                event.originalEventId()
        );

        boolean marketProduct = isMarketProduct(
                order.productType()
        );

        String orderStatus = marketProduct
                ? "SETTLEMENT_PENDING"
                : "ALLOTTED";

        String holdingStatus = marketProduct
                ? "SETTLEMENT_PENDING"
                : "ACTIVE";

        String folioOrDematReference = requiresDemat(
                order.requiresDemat()
        )
                ? findDematNumber(order.userId())
                : "FOL" + order.orderId();

        jdbcTemplate.update("""
                INSERT INTO investment_holdings (
                    user_id,
                    product_id,
                    source_order_id,
                    folio_or_demat_ref,
                    quantity,
                    tenure_months,
                    invested_amount,
                    average_cost,
                    current_value,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                order.userId(),
                order.productId(),
                order.orderId(),
                folioOrDematReference,
                order.quantity(),
                order.tenureMonths(),
                order.amount(),
                order.amount().divide(
                        order.quantity(),
                        4,
                        java.math.RoundingMode.HALF_UP
                ),
                order.amount(),
                holdingStatus
        );

        jdbcTemplate.update("""
                UPDATE investment_orders
                   SET status = ?,
                       banking_transaction_id = ?,
                       banking_reference_number = ?,
                       debited_at = SYSTIMESTAMP,
                       allotted_at = SYSTIMESTAMP,
                       updated_at = SYSTIMESTAMP
                 WHERE order_id = ?
                   AND status = 'DEBIT_PENDING'
                   AND debit_event_id = ?
                """,
                orderStatus,
                event.bankingTransactionId(),
                event.referenceNumber(),
                event.orderId(),
                event.originalEventId()
        );

        /*
         * Only scheduled orders have a schedule_id.
         * A normal lump-sum order leaves this value null.
         */
        if (order.scheduleId() != null) {
            recordSuccessfulScheduledInstallment(
                    order.scheduleId()
            );
        }

        saveProcessedEvent(
                event.eventId(),
                "banking.account.debit.succeeded.v1",
                "BankingDebitSucceeded",
                event.orderId()
        );

        // NEW: notify only after this transaction commits.
        investmentNotifications.afterCommit(
                order.userId(),
                "PAYMENT",
                "Investment debit completed for INR "
                        + order.amount().toPlainString()
                        + ". Order: " + order.orderId()
                        + ". Reference: " + event.referenceNumber()
                        + ". Status: " + orderStatus
        );

        // NEW: notify when the final scheduled installment completes.
        if (order.scheduleId() != null) {

            String scheduleStatus = jdbcTemplate.queryForObject(
                    """
                    SELECT status
                    FROM investment_schedules
                    WHERE schedule_id = ?
                    """,
                    String.class,
                    order.scheduleId()
            );

            if ("COMPLETED".equals(scheduleStatus)) {

                investmentNotifications.afterCommit(
                        order.userId(),
                        "PAYMENT",
                        "Your recurring investment schedule "
                                + "completed all installments. Schedule: "
                                + order.scheduleId()
                );
            }
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.banking-debit-failed}"
    )
    @Transactional
    public void handleDebitFailed(String payload) {

        BankingDebitFailedEvent event = readFailureEvent(payload);

        // Existing duplicate-event protection.
        if (alreadyProcessed(event.eventId())) {
            return;
        }

        int updatedRows = jdbcTemplate.update("""
                UPDATE investment_orders
                   SET status = 'DEBIT_FAILED',
                       failure_code = ?,
                       failure_reason = ?,
                       updated_at = SYSTIMESTAMP
                 WHERE order_id = ?
                   AND status = 'DEBIT_PENDING'
                   AND debit_event_id = ?
                """,
                event.failureCode(),
                event.failureReason(),
                event.orderId(),
                event.originalEventId()
        );

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "No matching Investment order is waiting for "
                            + "this Banking failure event."
            );
        }

        saveProcessedEvent(
                event.eventId(),
                "banking.account.debit.failed.v1",
                "BankingDebitFailed",
                event.orderId()
        );

        // NEW: obtain the notification recipient from the saved order.
        Long recipient = jdbcTemplate.queryForObject(
                """
                SELECT user_id
                FROM investment_orders
                WHERE order_id = ?
                """,
                Long.class,
                event.orderId()
        );

        investmentNotifications.afterCommit(
                recipient,
                "PAYMENT",
                "Your investment debit failed. Order: "
                        + event.orderId()
                        + ". Reason: " + event.failureReason()
        );
    }

    private void recordSuccessfulScheduledInstallment(
            Long scheduleId) {

        jdbcTemplate.update("""
                UPDATE investment_schedules
                   SET installments_completed =
                           installments_completed + 1,
                       last_debit_date =
                           ADD_MONTHS(next_debit_date, -1),
                       status = CASE
                           WHEN tenure_months IS NOT NULL
                            AND installments_completed + 1 >= tenure_months
                           THEN 'COMPLETED'
                           ELSE status
                       END,
                       completed_at = CASE
                           WHEN tenure_months IS NOT NULL
                            AND installments_completed + 1 >= tenure_months
                           THEN SYSTIMESTAMP
                           ELSE completed_at
                       END,
                       updated_at = SYSTIMESTAMP
                 WHERE schedule_id = ?
                """,
                scheduleId
        );
    }

    private InvestmentOrder findPendingOrder(
            Long orderId,
            String originalEventId) {

        Optional<InvestmentOrder> order = jdbcTemplate.query("""
                SELECT o.order_id,
                       o.user_id,
                       o.product_id,
                       o.schedule_id,
                       o.quantity,
                       o.selected_tenure_months,
                       o.amount,
                       p.product_type,
                       p.tenure_months AS product_tenure_months,
                       p.requires_demat
                  FROM investment_orders o
                  JOIN investment_products p
                    ON p.product_id = o.product_id
                 WHERE o.order_id = ?
                   AND o.status = 'DEBIT_PENDING'
                   AND o.debit_event_id = ?
                 FOR UPDATE
                """,
                this::mapOrder,
                orderId,
                originalEventId
        ).stream().findFirst();

        return order.orElseThrow(() ->
                new IllegalStateException(
                        "No matching Investment order is waiting "
                                + "for this Banking success event."
                )
        );
    }

    private String findDematNumber(Long userId) {

        String dematNumber = jdbcTemplate.queryForObject("""
                SELECT demat_number
                  FROM investment_profiles
                 WHERE user_id = ?
                   AND demat_status = 'ACTIVE'
                """,
                String.class,
                userId
        );

        if (dematNumber == null || dematNumber.isBlank()) {
            throw new IllegalStateException(
                    "A verified Demat account is required "
                            + "for this holding."
            );
        }

        return dematNumber;
    }

    private boolean alreadyProcessed(String eventId) {

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM investment_processed_events
                 WHERE event_id = ?
                """,
                Integer.class,
                eventId
        );

        return count != null && count > 0;
    }

    private void saveProcessedEvent(
            String eventId,
            String topicName,
            String eventType,
            Long orderId) {

        jdbcTemplate.update("""
                INSERT INTO investment_processed_events (
                    event_id,
                    topic_name,
                    event_type,
                    aggregate_id
                )
                VALUES (?, ?, ?, ?)
                """,
                eventId,
                topicName,
                eventType,
                orderId
        );
    }

    private BankingDebitSucceededEvent readSuccessEvent(
            String payload) {

        try {
            return objectMapper.readValue(
                    payload,
                    BankingDebitSucceededEvent.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid Banking debit-success Kafka event.",
                    exception
            );
        }
    }

    private BankingDebitFailedEvent readFailureEvent(
            String payload) {

        try {
            return objectMapper.readValue(
                    payload,
                    BankingDebitFailedEvent.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid Banking debit-failure Kafka event.",
                    exception
            );
        }
    }

    private InvestmentOrder mapOrder(
            ResultSet resultSet,
            int rowNumber) throws SQLException {

        Object scheduleIdValue = resultSet.getObject("schedule_id");

        Long scheduleId = scheduleIdValue instanceof Number number
                ? number.longValue()
                : null;

        Integer tenureMonths = nullableInteger(resultSet, "selected_tenure_months");
        if (tenureMonths == null) {
            tenureMonths = nullableInteger(resultSet, "product_tenure_months");
        }

        return new InvestmentOrder(
                resultSet.getLong("order_id"),
                resultSet.getLong("user_id"),
                resultSet.getLong("product_id"),
                scheduleId,
                resultSet.getBigDecimal("quantity"),
                tenureMonths,
                resultSet.getBigDecimal("amount"),
                resultSet.getString("product_type"),
                resultSet.getString("requires_demat")
        );
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value instanceof Number number ? number.intValue() : null;
    }

    private boolean requiresDemat(String value) {
        return "Y".equalsIgnoreCase(value);
    }

    private boolean isMarketProduct(String productType) {
        return "EQUITY".equals(productType)
                || "ETF".equals(productType);
    }

    private record InvestmentOrder(
            Long orderId,
            Long userId,
            Long productId,
            Long scheduleId,
            BigDecimal quantity,
            Integer tenureMonths,
            BigDecimal amount,
            String productType,
            String requiresDemat
    ) {
    }
}
