package com.inetbank.banking.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IdentityCustomerContactClient {

    private final RestClient restClient;
    private final String internalApiKey;

    public IdentityCustomerContactClient(
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

    public IdentityCustomerContactResponse getCustomerContact(
            Long userId) {

        IdentityCustomerContactResponse customer = restClient.get()
                .uri("/internal/customers/{userId}/email", userId)
                .header("X-Internal-Api-Key", internalApiKey)
                .retrieve()
                .body(IdentityCustomerContactResponse.class);

        if (customer == null
                || customer.email() == null
                || customer.email().isBlank()
                || customer.fullName() == null
                || customer.fullName().isBlank()) {

            throw new IllegalStateException(
                    "Identity did not return valid customer contact details."
            );
        }

        return customer;
    }
}