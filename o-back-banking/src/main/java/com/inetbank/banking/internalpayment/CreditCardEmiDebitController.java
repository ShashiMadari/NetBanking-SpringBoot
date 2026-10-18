package com.inetbank.banking.internalpayment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/credit-card-emi-payments")
public class CreditCardEmiDebitController {

    private final CreditCardEmiDebitService debitService;
    private final String bankingApiKey;

    public CreditCardEmiDebitController(
            CreditCardEmiDebitService debitService,
            @Value("${app.internal.banking-api-key}")
            String bankingApiKey) {

        this.debitService = debitService;
        this.bankingApiKey = bankingApiKey;
    }

    @PostMapping("/debit")
    public CreditCardEmiDebitResponse debit(
            @RequestHeader(
                    value = "X-Internal-Api-Key",
                    defaultValue = ""
            ) String suppliedApiKey,
            @Valid @RequestBody CreditCardEmiDebitRequest request) {

        if (!validApiKey(suppliedApiKey)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid internal API key."
            );
        }

        return debitService.debit(request);
    }

    private boolean validApiKey(String suppliedApiKey) {
        return MessageDigest.isEqual(
                bankingApiKey.getBytes(StandardCharsets.UTF_8),
                suppliedApiKey.getBytes(StandardCharsets.UTF_8)
        );
    }
}