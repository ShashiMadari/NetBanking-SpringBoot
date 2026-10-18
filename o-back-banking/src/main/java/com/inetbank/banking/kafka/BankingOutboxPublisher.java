package com.inetbank.banking.kafka;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BankingOutboxPublisher {

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public BankingOutboxPublisher(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate) {

        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox.fixed-delay-ms:5000}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = jdbcTemplate.query(
                """
                SELECT outbox_id, topic_name, event_key, payload
                FROM banking_kafka_outbox
                WHERE publish_status = 'PENDING'
                ORDER BY outbox_id
                FETCH FIRST 20 ROWS ONLY
                """,
                this::mapOutboxEvent);

        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(
                    event.topicName(),
                    event.eventKey(),
                    event.payload())
                    .get(10, TimeUnit.SECONDS);

            jdbcTemplate.update(
                    """
                    UPDATE banking_kafka_outbox
                    SET publish_status = 'PUBLISHED',
                        published_at = SYSTIMESTAMP,
                        publish_attempts = publish_attempts + 1,
                        last_error = NULL
                    WHERE outbox_id = ?
                      AND publish_status = 'PENDING'
                    """,
                    event.outboxId());

        } catch (Exception exception) {
            String errorMessage = exception.getMessage();

            if (errorMessage == null) {
                errorMessage = exception.getClass().getSimpleName();
            }

            if (errorMessage.length() > 1000) {
                errorMessage = errorMessage.substring(0, 1000);
            }

            jdbcTemplate.update(
                    """
                    UPDATE banking_kafka_outbox
                    SET publish_attempts = publish_attempts + 1,
                        last_error = ?
                    WHERE outbox_id = ?
                      AND publish_status = 'PENDING'
                    """,
                    errorMessage,
                    event.outboxId());
        }
    }

    private OutboxEvent mapOutboxEvent(
            ResultSet resultSet,
            int rowNumber) throws SQLException {

        return new OutboxEvent(
                resultSet.getLong("outbox_id"),
                resultSet.getString("topic_name"),
                resultSet.getString("event_key"),
                resultSet.getString("payload"));
    }

    private record OutboxEvent(
            long outboxId,
            String topicName,
            String eventKey,
            String payload) {
    }
}