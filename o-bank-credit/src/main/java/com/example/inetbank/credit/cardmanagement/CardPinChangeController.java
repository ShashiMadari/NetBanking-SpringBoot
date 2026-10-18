package com.example.inetbank.credit.cardmanagement;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cards/pin-change")
@PreAuthorize("hasRole('CUSTOMER')")
public class CardPinChangeController {

    private final CardPinChangeService cardPinChangeService;

    public CardPinChangeController(
            CardPinChangeService cardPinChangeService) {

        this.cardPinChangeService = cardPinChangeService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<CardPinChangeOtpResponse> initiate(
            @Valid @RequestBody InitiateCardPinChangeRequest request,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION)
            String bearerToken) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cardPinChangeService.initiate(
                        userId(authentication),
                        customerEmail(authentication),
                        bearerToken,
                        request
                ));
    }

    @PostMapping("/{pinChangeOrderId}/resend-otp")
    public CardPinChangeOtpResponse resendOtp(
            @PathVariable Long pinChangeOrderId,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION)
            String bearerToken) {

        return cardPinChangeService.resendOtp(
                userId(authentication),
                customerEmail(authentication),
                bearerToken,
                pinChangeOrderId
        );
    }

    @PostMapping("/{pinChangeOrderId}/confirm")
    public CardPinChangeConfirmationResponse confirm(
            @PathVariable Long pinChangeOrderId,
            @Valid @RequestBody ConfirmCardPinChangeRequest request,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION)
            String bearerToken) {

        return cardPinChangeService.confirm(
                userId(authentication),
                customerEmail(authentication),
                bearerToken,
                pinChangeOrderId,
                request
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