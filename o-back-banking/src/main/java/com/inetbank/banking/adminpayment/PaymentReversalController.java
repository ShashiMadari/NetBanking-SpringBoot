package com.inetbank.banking.adminpayment;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class PaymentReversalController {

    private final PaymentReversalService paymentReversalService;

    public PaymentReversalController(
            PaymentReversalService paymentReversalService) {

        this.paymentReversalService = paymentReversalService;
    }

    @PutMapping("/{paymentOrderId}/reverse")
    public ResponseEntity<PaymentReversalResponse> reverse(
            @PathVariable Long paymentOrderId,
            @Valid @RequestBody PaymentReversalRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            Authentication authentication) {

        Long adminUserId = Long.valueOf(authentication.getName());

        return ResponseEntity.ok(
                paymentReversalService.reverse(
                        adminUserId,
                        bearerToken,
                        paymentOrderId,
                        request.reason()
                )
        );
    }
}