package com.inetbank.identity.event;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inetbank.identity.notification.NotificationService;

@Component
public class ForexKafkaEventConsumer {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    ForexKafkaEventConsumer.class
            );

    private static final String TOPIC = "o-bank.forex.events";

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;

    public ForexKafkaEventConsumer(
            ObjectMapper objectMapper,
            NotificationService notificationService,
            JdbcTemplate jdbcTemplate) {

        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /*
     * KAFKA CHANGE:
     *
     * This one listener now handles every Forex event:
     * - conversion
     * - remittance
     * - Forex card load/spend
     * - statement email
     *
     * It creates both the customer notification and audit log entry.
     */
    @KafkaListener(
            topics = TOPIC,
            groupId = "o-bank-identity"
    )
    @Transactional
    public void consume(String json) {

        try {
            ForexIdentityEvent event =
                    objectMapper.readValue(
                            json,
                            ForexIdentityEvent.class
                    );

            validate(event);

            notificationService.create(
                    event.notificationUserId(),
                    event.notificationEventType(),
                    event.message()
            );

            jdbcTemplate.update("""
                    INSERT INTO audit_events (
                        user_id,
                        action,
                        entity_type,
                        entity_id,
                        ip_address,
                        created_at
                    )
                    VALUES (?, ?, ?, ?, ?, SYSTIMESTAMP)
                    """,
                    event.auditUserId(),
                    event.action(),
                    event.entityType(),
                    event.entityId(),
                    "FOREX-SERVICE"
            );

            LOG.info(
                    "Forex Kafka event processed. action={}, entity={}#{}",
                    event.action(),
                    event.entityType(),
                    event.entityId()
            );

        } catch (Exception exception) {
            LOG.error(
                    "Forex Kafka event processing failed. json={}",
                    json,
                    exception
            );

            throw new IllegalStateException(
                    "Forex Kafka event could not be processed.",
                    exception
            );
        }
    }

    private void validate(ForexIdentityEvent event) {

        if (event.notificationUserId() == null
                || event.auditUserId() == null
                || isBlank(event.notificationEventType())
                || isBlank(event.message())
                || isBlank(event.action())
                || isBlank(event.entityType())
                || isBlank(event.entityId())) {

            throw new IllegalArgumentException(
                    "Forex Kafka event contains required blank fields."
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /*
     * Must match ForexKafkaEventPublisher.ForexIdentityEvent.
     */
    public record ForexIdentityEvent(
            Long notificationUserId,
            Long auditUserId,
            String notificationEventType,
            String message,
            String action,
            String entityType,
            String entityId,
            LocalDateTime occurredAt
    ) {
    }
}