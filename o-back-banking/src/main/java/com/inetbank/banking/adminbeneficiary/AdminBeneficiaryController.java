package com.inetbank.banking.adminbeneficiary;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/beneficiaries")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBeneficiaryController {

    private final AdminBeneficiaryService service;

    public AdminBeneficiaryController(
            AdminBeneficiaryService service) {

        this.service = service;
    }

    @GetMapping("/pending")
    public List<PendingBeneficiaryResponse> pending() {
        return service.pending();
    }

    @PutMapping("/{id}/approve")
    public BeneficiaryApprovalResponse approve(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            Authentication authentication) {

        Long adminUserId = Long.valueOf(authentication.getName());

        return service.approve(
                id,
                adminUserId,
                bearerToken
        );
    }
}