package com.inetbank.banking.internalforex;

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
@RequestMapping("/internal/forex/conversions")
public class ForexConversionCreditController {

    private final ForexConversionCreditService service;
    private final String internalApiKey;

    public ForexConversionCreditController(
            ForexConversionCreditService service,
            @Value("${app.internal.banking-api-key}")
            String internalApiKey) {

        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/credit")
    public ResponseEntity<ForexConversionCreditResponse> credit(
            @RequestHeader(
                    value = "X-Internal-Api-Key",
                    defaultValue = ""
            )
            String providedApiKey,
            @Valid @RequestBody ForexConversionCreditRequest request) {

        if (!MessageDigest.isEqual(
                internalApiKey.getBytes(StandardCharsets.UTF_8),
                providedApiKey.getBytes(StandardCharsets.UTF_8))) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid internal API key."
            );
        }

        return ResponseEntity.ok(service.credit(request));
    }
}