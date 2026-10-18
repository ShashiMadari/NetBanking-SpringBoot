package com.example.inetbank.forex.remittance;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;

@RestController
@RequestMapping("/api/forex/remittances")
@PreAuthorize("hasRole('CUSTOMER')")
public class ForexOutwardRemittanceController {

    private final ForexOutwardRemittanceService remittanceService;

    public ForexOutwardRemittanceController(
            ForexOutwardRemittanceService remittanceService) {

        this.remittanceService = remittanceService;
    }

    @PostMapping("/outward")
    public ResponseEntity<ForexTransactionResponse> outward(
            @Valid @RequestBody OutwardRemittanceRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                remittanceService.remit(
                        Long.valueOf(authentication.getName()),
                        request
                )
        );
    }
}