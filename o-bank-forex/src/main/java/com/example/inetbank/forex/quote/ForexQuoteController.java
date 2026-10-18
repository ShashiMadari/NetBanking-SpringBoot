package com.example.inetbank.forex.quote;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/forex")
@PreAuthorize("hasRole('CUSTOMER')")
public class ForexQuoteController {

    private final ForexQuoteService quoteService;

    public ForexQuoteController(
            ForexQuoteService quoteService) {

        this.quoteService = quoteService;
    }

    @PostMapping("/quotes")
    public ResponseEntity<ForexQuoteResponse> createQuote(
            @Valid @RequestBody ForexQuoteRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                quoteService.createQuote(
                        Long.valueOf(authentication.getName()),
                        request
                )
        );
    }
}