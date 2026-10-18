package com.example.inetbank.forex.integration;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class BankingForexConversionClient {

    private final RestClient restClient;
    private final String bankingApiKey;

    public BankingForexConversionClient(
            @Value("${app.banking.base-url:http://localhost:8445}")
            String bankingBaseUrl,
            @Value("${app.internal.banking-api-key}")
            String bankingApiKey) {

        this.restClient = RestClient.builder()
                .baseUrl(bankingBaseUrl)
                .build();

        this.bankingApiKey = bankingApiKey;
    }

    public BankingForexConversionDebitResponse debitInrAccount(
            Long userId,
            Long sourceAccountId,
            BigDecimal amount,
            String referenceNumber) {

        return restClient.post()
                .uri("/internal/forex/conversions/debit")
                .header("X-Internal-Api-Key", bankingApiKey)
                .body(new BankingForexConversionDebitRequest(
                        userId,
                        sourceAccountId,
                        amount,
                        referenceNumber
                ))
                .retrieve()
                .body(BankingForexConversionDebitResponse.class);
    }

    public BankingForexConversionCreditResponse creditInrAccount(
            Long userId,
            Long destinationAccountId,
            BigDecimal amount,
            String referenceNumber) {

        return restClient.post()
                .uri("/internal/forex/conversions/credit")
                .header("X-Internal-Api-Key", bankingApiKey)
                .body(new BankingForexConversionCreditRequest(
                        userId,
                        destinationAccountId,
                        amount,
                        referenceNumber
                ))
                .retrieve()
                .body(BankingForexConversionCreditResponse.class);
    }
}