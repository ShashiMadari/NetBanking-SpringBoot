package com.inetbank.banking.fundtransfer;

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
@RequestMapping("/api/transfers")
@PreAuthorize("hasRole('CUSTOMER')")
public class TransferController {

    private final TransferService transferService;

    TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/initiate")
    ResponseEntity<TransferOtpResponse> initiate(
            @Valid @RequestBody InitiateTransferRequest request,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());
        String customerEmail = (String) authentication.getDetails();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.initiate(
                        userId,
                        customerEmail,
                        request
                ));
    }

    @PostMapping("/{paymentOrderId}/confirm")
    TransferConfirmationResponse confirm(
            @PathVariable Long paymentOrderId,
            @Valid @RequestBody ConfirmTransferRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());
        String customerEmail = (String) authentication.getDetails();

        return transferService.confirm(
                userId,
                customerEmail,
                bearerToken,
                paymentOrderId,
                request
        );
    }

    @PostMapping("/{paymentOrderId}/resend-otp")
    TransferOtpResponse resendOtp(
            @PathVariable Long paymentOrderId,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());
        String customerEmail = (String) authentication.getDetails();

        return transferService.resendOtp(
                userId,
                customerEmail,
                paymentOrderId
        );
    }
}