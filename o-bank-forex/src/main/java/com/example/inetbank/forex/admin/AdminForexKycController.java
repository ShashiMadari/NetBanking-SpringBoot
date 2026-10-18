package com.example.inetbank.forex.admin;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/forex/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminForexKycController {

    private final AdminForexKycService kycService;

    public AdminForexKycController(
            AdminForexKycService kycService) {

        this.kycService = kycService;
    }

    @PutMapping("/{userId}/kyc")
    public ResponseEntity<Void> updateKyc(
            @PathVariable Long userId,
            @Valid @RequestBody KycStatusRequest request,
            Authentication authentication) {

        kycService.updateKyc(
                Long.valueOf(authentication.getName()),
                userId,
                request
        );

        return ResponseEntity.noContent().build();
    }
}