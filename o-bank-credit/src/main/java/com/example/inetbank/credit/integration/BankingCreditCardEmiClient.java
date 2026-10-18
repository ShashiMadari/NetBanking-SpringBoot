package com.example.inetbank.credit.integration;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class BankingCreditCardEmiClient {

    private final RestClient.Builder restClientBuilder;
    private final String bankingApiKey;
    private final String bankingBaseUrl;

    public BankingCreditCardEmiClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.internal.banking-api-key}")
            String bankingApiKey,
            @Value("${app.banking.base-url:http://localhost:8445}")
            String bankingBaseUrl) {

        this.restClientBuilder = restClientBuilder;
        this.bankingApiKey = bankingApiKey;
        this.bankingBaseUrl = bankingBaseUrl;
    }

    public BankingCreditCardEmiDebitResponse debit(
            Long userId,
            Long sourceAccountId,
            BigDecimal amount,
            String referenceNumber) {

        return restClientBuilder.build()
                .post()
                .uri(bankingBaseUrl
                        + "/internal/credit-card-emi-payments/debit")
                .header(
                        "X-Internal-Api-Key",
                        bankingApiKey
                )
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        "application/json"
                )
                .body(new BankingCreditCardEmiDebitRequest(
                        userId,
                        sourceAccountId,
                        amount,
                        referenceNumber
                ))
                .retrieve()
                .body(BankingCreditCardEmiDebitResponse.class);
    }
}
