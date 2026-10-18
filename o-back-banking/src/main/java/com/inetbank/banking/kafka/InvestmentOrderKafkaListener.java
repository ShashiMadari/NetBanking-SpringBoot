package com.inetbank.banking.kafka;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inetbank.banking.investmentpayment.InvestmentPurchaseDebitRequest;
import com.inetbank.banking.investmentpayment.InvestmentPurchaseDebitResponse;
import com.inetbank.banking.investmentpayment.InvestmentPurchaseDebitService;
import com.inetbank.banking.kafka.event.BankingDebitFailedEvent;
import com.inetbank.banking.kafka.event.BankingDebitSucceededEvent;
import com.inetbank.banking.kafka.event.InvestmentOrderPlacedEvent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InvestmentOrderKafkaListener {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final InvestmentPurchaseDebitService debitService;

    private final String successTopic;
    private final String failureTopic;

    public InvestmentOrderKafkaListener(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            InvestmentPurchaseDebitService debitService,
            @Value("${app.kafka.topics.banking-debit-succeeded}")
            String successTopic,
            @Value("${app.kafka.topics.banking-debit-failed}")
            String failureTopic) {

        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.debitService = debitService;
        this.successTopic = successTopic;
        this.failureTopic = failureTopic;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.investment-order-placed}",
            groupId = "o-bank-banking"
    )
    @Transactional
    public void handleInvestmentOrder(String payload) {
        InvestmentOrderPlacedEvent event = fromJson(payload);

        if (alreadyProcessed(event.eventId())) {
            return;
        }

        try {
            InvestmentPurchaseDebitResponse debitResponse =
                    debitService.debit(
                            new InvestmentPurchaseDebitRequest(
                                    event.userId(),
                                    event.accountId(),
                                    event.amount(),
                                    event.referenceNumber()));

            queueSuccessEvent(event, debitResponse);

        } catch (ResponseStatusException exception) {
            queueFailureEvent(event, exception);
        }

        jdbcTemplate.update(
                """
                INSERT INTO banking_processed_events (
                    event_id,
                    topic_name,
                    event_type
                )
                VALUES (?, ?, ?)
                """,
                event.eventId(),
                "investment.order.placed.v1",
                "InvestmentOrderPlaced");
    }

    private boolean alreadyProcessed(String eventId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM banking_processed_events
                WHERE event_id = ?
                """,
                Integer.class,
                eventId);

        return count != null && count > 0;
    }

    private void queueSuccessEvent(
            InvestmentOrderPlacedEvent event,
            InvestmentPurchaseDebitResponse debitResponse) {

        BankingDebitSucceededEvent successEvent =
                new BankingDebitSucceededEvent(
                        UUID.randomUUID().toString(),
                        event.eventId(),
                        event.orderId(),
                        event.userId(),
                        event.accountId(),
                        event.amount(),
                        debitResponse.transactionId(),
                        event.referenceNumber(),
                        Instant.now());

        insertOutboxEvent(
                successEvent.eventId(),
                event.eventId(),
                debitResponse.transactionId(),
                "BankingDebitSucceeded",
                successTopic,
                String.valueOf(event.orderId()),
                toJson(successEvent));
    }

    private void queueFailureEvent(
            InvestmentOrderPlacedEvent event,
            ResponseStatusException exception) {

        String failureCode = exception.getStatusCode().value() == 409
                ? "REFERENCE_CONFLICT"
                : "DEBIT_REJECTED";

        BankingDebitFailedEvent failedEvent =
                new BankingDebitFailedEvent(
                        UUID.randomUUID().toString(),
                        event.eventId(),
                        event.orderId(),
                        event.userId(),
                        event.accountId(),
                        event.amount(),
                        failureCode,
                        exception.getReason(),
                        Instant.now());

        insertOutboxEvent(
                failedEvent.eventId(),
                event.eventId(),
                null,
                "BankingDebitFailed",
                failureTopic,
                String.valueOf(event.orderId()),
                toJson(failedEvent));
    }

    private void insertOutboxEvent(
            String eventId,
            String originalEventId,
            Long bankingTransactionId,
            String eventType,
            String topicName,
            String eventKey,
            String payload) {

        jdbcTemplate.update(
                """
                INSERT INTO banking_kafka_outbox (
                    event_id,
                    original_event_id,
                    banking_transaction_id,
                    event_type,
                    topic_name,
                    event_key,
                    payload,
                    publish_status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """,
                eventId,
                originalEventId,
                bankingTransactionId,
                eventType,
                topicName,
                eventKey,
                payload);
    }

    private InvestmentOrderPlacedEvent fromJson(String payload) {
        try {
            return objectMapper.readValue(
                    payload,
                    InvestmentOrderPlacedEvent.class);

        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid Investment Kafka event payload.",
                    exception);
        }
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to create the Banking Kafka result event.",
                    exception);
        }
    }
}