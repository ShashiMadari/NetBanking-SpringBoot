package com.example.inetbank.forex.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class IdentityCustomerEmailClient {

    private final RestClient restClient;
    private final String internalApiKey;

    public IdentityCustomerEmailClient(
            @Value("${app.identity.base-url:http://localhost:8444}")
            String identityBaseUrl,
            @Value("${app.internal.identity-api-key}")
            String internalApiKey) {

        this.restClient = RestClient.builder()
                .baseUrl(identityBaseUrl)
                .build();

        this.internalApiKey = internalApiKey;
    }

    public String getEmail(Long userId) {
        try {
            CustomerEmailResponse response = restClient.get()
                    .uri("/internal/customers/{userId}/email", userId)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CustomerEmailResponse.class);

            if (response == null
                    || response.email() == null
                    || response.email().isBlank()) {

                throw new IllegalStateException(
                        "Identity did not return a customer email."
                );
            }

            return response.email();
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Customer email could not be retrieved from Identity.",
                    exception
            );
        }
    }

    private record CustomerEmailResponse(
            Long userId,
            String email
    ) {
    }
}