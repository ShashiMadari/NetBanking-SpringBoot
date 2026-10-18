package com.example.inetbank.forex.event;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ForexKafkaEventPublisher {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    ForexKafkaEventPublisher.class
            );

    private static final String TOPIC = "o-bank.forex.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ForexKafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {

        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /*
     * Generic Kafka publisher.
     *
     * Every completed Forex action can call this method:
     * conversion, remittance, card load, card spend, statement email, etc.
     */
    public void publish(
            ForexIdentityEvent event,
            String messageKey) {

        try {
            String json = objectMapper.writeValueAsString(
                    event
            );

            kafkaTemplate.send(
                    TOPIC,
                    messageKey,
                    json
            ).whenComplete((result, exception) -> {
                if (exception == null) {
                    LOG.info(
                            "Forex Kafka event published. action={}, entity={}#{}",
                            event.action(),
                            event.entityType(),
                            event.entityId()
                    );
                } else {
                    LOG.error(
                            "Forex Kafka event could not be published. action={}",
                            event.action(),
                            exception
                    );
                }
            });

        } catch (JsonProcessingException exception) {
            LOG.error(
                    "Could not convert Forex Kafka event to JSON. action={}",
                    event.action(),
                    exception
            );
        }
    }

    /*
     * KAFKA DEMO COMPATIBILITY METHOD:
     * Your existing ForexConversionService can continue calling this
     * method while we migrate the remaining Forex services.
     */
    public void publishConversionCompleted(
            Long userId,
            Long forexTransactionId,
            String referenceNumber,
            String message) {

        publish(
                new ForexIdentityEvent(
                        userId,
                        userId,
                        "PAYMENT",
                        message,
                        "FOREX_CONVERSION_COMPLETED",
                        "FOREX_TRANSACTION",
                        String.valueOf(forexTransactionId),
                        LocalDateTime.now()
                ),
                referenceNumber
        );
    }

    /*
     * notificationUserId:
     * Customer who receives the in-app notification.
     *
     * auditUserId:
     * User whose action is written to the audit log.
     * For customer actions, both IDs are the same.
     * For admin inward remittance, they can be different.
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