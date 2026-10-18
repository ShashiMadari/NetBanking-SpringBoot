package com.example.inetbank.credit.integration;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class IdentityNotificationClient {

    private final RestClient.Builder restClientBuilder;

    public IdentityNotificationClient(
            @LoadBalanced RestClient.Builder restClientBuilder) {

        this.restClientBuilder = restClientBuilder;
    }

    public void createNotification(
            String bearerToken,
            String eventType,
            String message) {

        try {
            restClientBuilder.build()
                    .post()
                    .uri("http://O-BANK-IDENTITY/internal/notifications")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreateNotificationRequest(eventType, message))
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Could not create notification in Identity service.",
                    exception
            );
        }
    }

    public void createAdminNotification(
            String bearerToken,
            Long customerUserId,
            String eventType,
            String message) {

        try {
            restClientBuilder.build()
                    .post()
                    .uri("http://O-BANK-IDENTITY/internal/notifications/admin")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreateAdminNotificationRequest(
                            customerUserId,
                            eventType,
                            message
                    ))
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Could not create customer notification in Identity service.",
                    exception
            );
        }
    }

    private record CreateNotificationRequest(
            String eventType,
            String message
    ) {
    }

    private record CreateAdminNotificationRequest(
            Long userId,
            String eventType,
            String message
    ) {
    }


}