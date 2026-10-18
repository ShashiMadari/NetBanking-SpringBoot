package com.example.inetbank.credit.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IdentityScheduledNotificationClient {

    private final RestClient restClient;
    private final String internalApiKey;

    public IdentityScheduledNotificationClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.identity.base-url:http://localhost:8444}")
            String identityBaseUrl,
            @Value("${app.internal.identity-api-key}")
            String internalApiKey) {

        this.restClient = restClientBuilder
                .baseUrl(identityBaseUrl)
                .build();

        this.internalApiKey = internalApiKey;
    }

    public void createNotification(
            Long userId,
            String eventType,
            String message) {

        restClient.post()
                .uri("/internal/notifications/system")
                .header("X-Internal-Api-Key", internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new IdentityScheduledNotificationRequest(
                        userId,
                        eventType,
                        message
                ))
                .retrieve()
                .toBodilessEntity();
    }
}