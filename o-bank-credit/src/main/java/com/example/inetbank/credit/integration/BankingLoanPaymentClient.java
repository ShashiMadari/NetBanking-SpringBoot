package com.example.inetbank.credit.integration;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class BankingLoanPaymentClient {

    private final RestClient.Builder restClientBuilder;
    private final String bankingApiKey;

    public BankingLoanPaymentClient(
            @LoadBalanced RestClient.Builder restClientBuilder,
            @Value("${app.internal.banking-api-key}")
            String bankingApiKey) {

        this.restClientBuilder = restClientBuilder;
        this.bankingApiKey = bankingApiKey;
    }

    public BankingLoanPaymentDebitResponse debit(
            Long userId,
            Long sourceAccountId,
            String paymentType,
            BigDecimal amount,
            String referenceNumber) {

        return restClientBuilder.build()
                .post()
                .uri("http://O-BANK-BANKING/internal/loan-payments/debit")
                .header("X-Internal-Api-Key", bankingApiKey)
                .body(new BankingLoanPaymentDebitRequest(
                        userId,
                        sourceAccountId,
                        paymentType,
                        amount,
                        referenceNumber
                ))
                .retrieve()
                .body(BankingLoanPaymentDebitResponse.class);
    }
}