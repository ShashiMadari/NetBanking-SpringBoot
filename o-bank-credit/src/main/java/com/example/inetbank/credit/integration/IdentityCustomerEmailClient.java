package com.example.inetbank.credit.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IdentityCustomerEmailClient {

    private final RestClient restClient;
    private final String internalApiKey;

    public IdentityCustomerEmailClient(
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

    public String getCustomerEmail(Long userId) {
        return getCustomerContact(userId).email();
    }

    public IdentityCustomerEmailResponse getCustomerContact(
            Long userId) {

        IdentityCustomerEmailResponse response = restClient.get()
                .uri("/internal/customers/{userId}/email", userId)
                .header("X-Internal-Api-Key", internalApiKey)
                .retrieve()
                .body(IdentityCustomerEmailResponse.class);

        if (response == null
                || response.userId() == null
                || !userId.equals(response.userId())
                || response.email() == null
                || response.email().isBlank()
                || response.fullName() == null
                || response.fullName().isBlank()) {

            throw new IllegalStateException(
                    "Identity service did not return valid customer contact details."
            );
        }

        return response;
    }
}