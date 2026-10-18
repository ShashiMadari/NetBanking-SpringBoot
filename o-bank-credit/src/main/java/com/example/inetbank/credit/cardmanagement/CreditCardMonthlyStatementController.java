package com.example.inetbank.credit.cardmanagement;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credit-cards")
@PreAuthorize("hasRole('CUSTOMER')")
public class CreditCardMonthlyStatementController {

    private final CreditCardMonthlyStatementService statementService;

    public CreditCardMonthlyStatementController(
            CreditCardMonthlyStatementService statementService) {

        this.statementService = statementService;
    }

    @GetMapping("/{cardId}/monthly-statement")
    public CreditCardMonthlyStatementResponse monthlyStatement(
            @PathVariable Long cardId,
            @RequestParam int year,
            @RequestParam int month,
            Authentication authentication) {

        return statementService.getStatement(
                userId(authentication),
                cardId,
                year,
                month
        );
    }

    @PostMapping("/{cardId}/monthly-statement/email")
    public ResponseEntity<Void> emailMonthlyStatement(
            @PathVariable Long cardId,
            @RequestParam int year,
            @RequestParam int month,
            Authentication authentication) {

        statementService.emailStatement(
                userId(authentication),
                customerEmail(authentication),
                cardId,
                year,
                month
        );

        return ResponseEntity.accepted().build();
    }

    private Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    private String customerEmail(Authentication authentication) {
        Object details = authentication.getDetails();

        if (details instanceof String email && !email.isBlank()) {
            return email;
        }

        throw new IllegalStateException(
                "Customer email is missing from the JWT authentication."
        );
    }
}