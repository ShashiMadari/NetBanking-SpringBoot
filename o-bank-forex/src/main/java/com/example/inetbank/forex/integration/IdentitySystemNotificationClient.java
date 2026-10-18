package com.example.inetbank.forex.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class IdentitySystemNotificationClient {

    private static final Logger LOG = LoggerFactory.getLogger(
            IdentitySystemNotificationClient.class
    );

    private final RestClient restClient;
    private final String internalApiKey;

    public IdentitySystemNotificationClient(
            @Value("${app.identity.base-url:http://localhost:8444}")
            String identityBaseUrl,
            @Value("${app.internal.identity-api-key}")
            String internalApiKey) {

        this.restClient = RestClient.builder()
                .baseUrl(identityBaseUrl)
                .build();

        this.internalApiKey = internalApiKey;
    }

    public void createNotification(
            Long userId,
            String eventType,
            String message) {

        try {
            restClient.post()
                    .uri("/internal/notifications/system")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SystemNotificationRequest(
                            userId,
                            eventType,
                            message
                    ))
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException exception) {
            LOG.warn(
                    "Identity notification could not be created. "
                            + "userId={}, eventType={}",
                    userId,
                    eventType,
                    exception
            );
        }
    }

    public void createAuditEvent(
            Long userId,
            String action,
            String entityType,
            String entityId) {

        try {
            restClient.post()
                    .uri("/internal/system/audit-events")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SystemAuditEventRequest(
                            userId,
                            action,
                            entityType,
                            entityId,
                            "FOREX-SERVICE"
                    ))
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException exception) {
            LOG.warn(
                    "Identity audit event could not be created. "
                            + "userId={}, action={}",
                    userId,
                    action,
                    exception
            );
        }
    }

    private record SystemNotificationRequest(
            Long userId,
            String eventType,
            String message
    ) {
    }

    private record SystemAuditEventRequest(
            Long userId,
            String action,
            String entityType,
            String entityId,
            String ipAddress
    ) {
    }
}