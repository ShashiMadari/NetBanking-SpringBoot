package com.example.inetbank.forex.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.inetbank.forex.compliance.ComplianceFlagResponse;
import com.example.inetbank.forex.compliance.ComplianceFlagService;
import com.example.inetbank.forex.compliance.ComplianceReviewRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/forex/compliance-flags")
@PreAuthorize("hasRole('ADMIN')")
public class AdminForexComplianceController {

    private final ComplianceFlagService complianceFlags;

    public AdminForexComplianceController(
            ComplianceFlagService complianceFlags) {

        this.complianceFlags = complianceFlags;
    }

    @GetMapping
    public List<ComplianceFlagResponse> list(
            @RequestParam(required = false) String status) {

        return complianceFlags.findAll(status);
    }

    @PutMapping("/{complianceFlagId}")
    public ResponseEntity<ComplianceFlagResponse> review(
            @PathVariable Long complianceFlagId,
            @Valid @RequestBody ComplianceReviewRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                complianceFlags.review(
                        Long.valueOf(authentication.getName()),
                        complianceFlagId,
                        request
                )
        );
    }
}