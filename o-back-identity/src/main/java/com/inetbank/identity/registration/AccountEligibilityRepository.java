package com.inetbank.identity.registration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls Banking; Identity must never read Banking's accounts table directly. */
@Component
public class AccountEligibilityRepository {

    private final RestClient bankingClient;
    private final String internalApiKey;

    AccountEligibilityRepository(
            @Value("${app.banking.base-url:http://localhost:8445}")
            String bankingBaseUrl,
            @Value("${app.internal.banking-api-key}")
            String internalApiKey) {

        this.bankingClient = RestClient.builder().baseUrl(bankingBaseUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public boolean hasActiveAccount(Long userId) {
        try {
            ActiveAccountResponse response = bankingClient.get()
                    .uri("/internal/accounts/{userId}/active", userId)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve()
                    .body(ActiveAccountResponse.class);

            return response != null && response.active();
        } catch (RestClientException exception) {
            throw new RegistrationException(
                    "Banking account eligibility could not be verified."
            );
        }
    }

    private record ActiveAccountResponse(boolean active) {
    }
}
