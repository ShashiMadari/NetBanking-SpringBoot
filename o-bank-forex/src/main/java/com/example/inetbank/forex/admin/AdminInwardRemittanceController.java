package com.example.inetbank.forex.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/forex/remittances")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInwardRemittanceController {

    private final AdminInwardRemittanceService remittanceService;

    public AdminInwardRemittanceController(
            AdminInwardRemittanceService remittanceService) {

        this.remittanceService = remittanceService;
    }

    @PostMapping("/inward/simulate")
    public ResponseEntity<ForexTransactionResponse> simulate(
            @Valid @RequestBody InwardRemittanceRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                remittanceService.simulate(
                        Long.valueOf(authentication.getName()),
                        request
                )
        );
    }
}