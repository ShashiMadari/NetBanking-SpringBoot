package com.example.inetbank.credit.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class IdentitySystemNotificationClient {

    private final RestClient restClient;
    private final String internalApiKey;

    public IdentitySystemNotificationClient(
            @LoadBalanced RestClient.Builder restClientBuilder,
            @Value("${app.internal.identity-api-key}")
            String internalApiKey) {

        this.restClient = restClientBuilder.build();
        this.internalApiKey = internalApiKey;
    }

    public void notifyPaymentOtpExpired(
            Long customerUserId,
            Long paymentOrderId,
            String referenceNumber) {

        try {
            restClient.post()
                    .uri(
                            "http://O-BANK-IDENTITY"
                                    + "/internal/system/payment-otp-expired"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .body(new PaymentOtpExpiredRequest(
                            customerUserId,
                            paymentOrderId,
                            referenceNumber
                    ))
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Identity notification service could not be reached.",
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
                    .uri(
                            "http://O-BANK-IDENTITY"
                                    + "/internal/system/audit-events"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .body(new SystemAuditEventRequest(
                            userId,
                            action,
                            entityType,
                            entityId,
                            "BANKING-SERVICE"
                    ))
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Identity audit service could not be reached.",
                    exception
            );
        }
    }

    private record PaymentOtpExpiredRequest(
            Long customerUserId,
            Long paymentOrderId,
            String referenceNumber) {
    }

    private record SystemAuditEventRequest(
            Long userId,
            String action,
            String entityType,
            String entityId,
            String ipAddress) {
    }
}