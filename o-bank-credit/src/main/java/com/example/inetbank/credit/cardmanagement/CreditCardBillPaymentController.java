package com.example.inetbank.credit.cardmanagement;

import jakarta.validation.Valid;

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

@RestController
@RequestMapping("/api/credit-card-bill-payments")
@PreAuthorize("hasRole('CUSTOMER')")
public class CreditCardBillPaymentController {

    private final CreditCardBillPaymentService paymentService;

    public CreditCardBillPaymentController(
            CreditCardBillPaymentService paymentService) {

        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<CreditCardBillPaymentOtpResponse> initiate(
            @Valid
            @RequestBody InitiateCreditCardBillPaymentRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.initiate(
                        userId(authentication),
                        customerEmail(authentication),
                        bearerToken,
                        request
                ));
    }

    @PostMapping("/{paymentOrderId}/resend-otp")
    public CreditCardBillPaymentOtpResponse resendOtp(
            @PathVariable Long paymentOrderId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            Authentication authentication) {

        return paymentService.resendOtp(
                userId(authentication),
                customerEmail(authentication),
                bearerToken,
                paymentOrderId
        );
    }

    @PostMapping("/{paymentOrderId}/confirm")
    public CreditCardBillPaymentConfirmationResponse confirm(
            @PathVariable Long paymentOrderId,
            @Valid
            @RequestBody ConfirmCreditCardBillPaymentRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            Authentication authentication) {

        return paymentService.confirm(
                userId(authentication),
                customerEmail(authentication),
                bearerToken,
                paymentOrderId,
                request
        );
    }

    private Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    private String customerEmail(Authentication authentication) {
        Object details = authentication.getDetails();

        return details instanceof String email ? email : null;
    }
}