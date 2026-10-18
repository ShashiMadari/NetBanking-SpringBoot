package com.inetbank.banking.internalloanpayment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/loan-payments")
public class LoanPaymentDebitController {

    private final LoanPaymentDebitService service;
    private final String internalApiKey;

    public LoanPaymentDebitController(
            LoanPaymentDebitService service,
            @Value("${app.internal.banking-api-key}")
            String internalApiKey) {

        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/debit")
    public ResponseEntity<LoanPaymentDebitResponse> debit(
            @RequestHeader(
                    value = "X-Internal-Api-Key",
                    defaultValue = ""
            )
            String providedApiKey,
            @Valid @RequestBody LoanPaymentDebitRequest request) {

        if (!MessageDigest.isEqual(
                internalApiKey.getBytes(StandardCharsets.UTF_8),
                providedApiKey.getBytes(StandardCharsets.UTF_8))) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid internal API key."
            );
        }

        return ResponseEntity.ok(service.debit(request));
    }
}