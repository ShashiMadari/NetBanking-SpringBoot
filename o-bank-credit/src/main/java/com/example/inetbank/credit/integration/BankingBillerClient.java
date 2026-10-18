package com.example.inetbank.credit.integration;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class BankingBillerClient {

    private final RestClient.Builder restClientBuilder;
    private final String bankingBaseUrl;

    public BankingBillerClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.banking.base-url:http://localhost:8445}")
            String bankingBaseUrl) {

        this.restClientBuilder = restClientBuilder;
        this.bankingBaseUrl = bankingBaseUrl;
    }

    public BankingBillerResponse activeBiller(
            String bearerToken,
            Long billerId) {

        List<BankingBillerResponse> billers =
                restClientBuilder.build()
                        .get()
                        .uri(bankingBaseUrl + "/api/billers")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken
                        )
                        .retrieve()
                        .body(new ParameterizedTypeReference<
                                List<BankingBillerResponse>>() {
                        });

        if (billers == null) {
            throw new IllegalStateException(
                    "Banking biller service returned no billers."
            );
        }

        return billers.stream()
                .filter(biller -> biller.id().equals(billerId))
                .filter(biller -> "ACTIVE".equals(biller.status()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Selected biller is not active."
                ));
    }

    public record BankingBillerResponse(
            Long id,
            String billerName,
            String billerCategory,
            String consumerNumberLabel,
            String status) {
    }
}
