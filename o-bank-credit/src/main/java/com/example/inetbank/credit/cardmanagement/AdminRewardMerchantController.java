package com.example.inetbank.credit.cardmanagement;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reward-merchants")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRewardMerchantController {

    private final AdminRewardMerchantService merchantService;

    public AdminRewardMerchantController(
            AdminRewardMerchantService merchantService) {

        this.merchantService = merchantService;
    }

    @GetMapping
    public List<RewardMerchantResponse> list() {
        return merchantService.list();
    }

    @PostMapping
    public ResponseEntity<RewardMerchantResponse> create(
            @Valid @RequestBody CreateRewardMerchantRequest request,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(merchantService.create(
                        adminUserId(authentication),
                        request
                ));
    }

    @PatchMapping("/{merchantId}/status")
    public RewardMerchantResponse updateStatus(
            @PathVariable Long merchantId,
            @Valid @RequestBody RewardMerchantStatusRequest request,
            Authentication authentication) {

        return merchantService.updateStatus(
                adminUserId(authentication),
                merchantId,
                request
        );
    }

    @PatchMapping("/{merchantId}/reward-eligibility")
    public RewardMerchantResponse updateRewardEligibility(
            @PathVariable Long merchantId,
            @Valid @RequestBody
            RewardMerchantEligibilityRequest request,
            Authentication authentication) {

        return merchantService.updateRewardEligibility(
                adminUserId(authentication),
                merchantId,
                request
        );
    }

    private Long adminUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}