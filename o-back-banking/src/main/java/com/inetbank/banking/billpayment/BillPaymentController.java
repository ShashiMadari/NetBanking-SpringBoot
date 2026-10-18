package com.inetbank.banking.billpayment;

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
@RequestMapping("/api/bill-payments")
@PreAuthorize("hasRole('CUSTOMER')")
public class BillPaymentController {

    private final BillPaymentService billPaymentService;

    BillPaymentController(BillPaymentService billPaymentService) {
        this.billPaymentService = billPaymentService;
    }

    @PostMapping("/initiate")
    ResponseEntity<BillPaymentOtpResponse> initiate(
            @Valid @RequestBody BillPaymentRequest request,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());
        String customerEmail = (String) authentication.getDetails();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(billPaymentService.initiate(
                        userId,
                        customerEmail,
                        request
                ));
    }

    @PostMapping("/{paymentOrderId}/confirm")
    BillPaymentConfirmationResponse confirm(
            @PathVariable Long paymentOrderId,
            @Valid @RequestBody ConfirmBillPaymentRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());
        String customerEmail = (String) authentication.getDetails();

        return billPaymentService.confirm(
                userId,
                customerEmail,
                bearerToken,
                paymentOrderId,
                request
        );
    }

    @PostMapping("/{paymentOrderId}/resend-otp")
    BillPaymentOtpResponse resendOtp(
            @PathVariable Long paymentOrderId,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());
        String customerEmail = (String) authentication.getDetails();

        return billPaymentService.resendOtp(
                userId,
                customerEmail,
                paymentOrderId
        );
    }
}