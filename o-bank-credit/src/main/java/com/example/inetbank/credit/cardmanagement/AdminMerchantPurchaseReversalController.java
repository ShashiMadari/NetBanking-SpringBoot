package com.example.inetbank.credit.cardmanagement;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/credit-card-rewards")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMerchantPurchaseReversalController {

    private final AdminMerchantPurchaseReversalService reversalService;

    public AdminMerchantPurchaseReversalController(
            AdminMerchantPurchaseReversalService reversalService) {

        this.reversalService = reversalService;
    }

    @PostMapping("/purchases/{merchantPurchaseId}/reverse")
    public ResponseEntity<MerchantPurchaseReversalResponse> reverse(
            @PathVariable Long merchantPurchaseId,
            @Valid @RequestBody MerchantPurchaseReversalRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                reversalService.reverse(
                        Long.valueOf(authentication.getName()),
                        merchantPurchaseId,
                        request
                )
        );
    }
}