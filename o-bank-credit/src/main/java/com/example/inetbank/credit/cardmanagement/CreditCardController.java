package com.example.inetbank.credit.cardmanagement;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cards")
@PreAuthorize("hasRole('CUSTOMER')")
public class CreditCardController {

    private final CreditCardService creditCardService;

    public CreditCardController(
            CreditCardService creditCardService) {

        this.creditCardService = creditCardService;
    }

    @GetMapping
    public List<CreditCardResponse> list(
            Authentication authentication) {

        return creditCardService.list(
                userId(authentication)
        );
    }

    @PutMapping("/{cardId}/block")
    public CardActionResponse block(
            @PathVariable Long cardId,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION)
            String bearerToken) {

        return creditCardService.block(
                cardId,
                userId(authentication),
                customerEmail(authentication),
                bearerToken
        );
    }

    @PutMapping("/{cardId}/unblock")
    public CardActionResponse unblock(
            @PathVariable Long cardId,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION)
            String bearerToken) {

        return creditCardService.unblock(
                cardId,
                userId(authentication),
                customerEmail(authentication),
                bearerToken
        );
    }

    @PutMapping("/{cardId}/daily-limit")
    public CardActionResponse updateDailyLimit(
            @PathVariable Long cardId,
            @Valid @RequestBody UpdateCardLimitRequest request,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION)
            String bearerToken) {

        return creditCardService.updateDailyLimit(
                cardId,
                userId(authentication),
                customerEmail(authentication),
                bearerToken,
                request.dailyLimit()
        );
    }

    private Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    private String customerEmail(Authentication authentication) {
        Object details = authentication.getDetails();

        return details instanceof String email
                ? email
                : null;
    }
}