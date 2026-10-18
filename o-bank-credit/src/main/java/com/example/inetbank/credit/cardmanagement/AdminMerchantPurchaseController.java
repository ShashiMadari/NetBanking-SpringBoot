package com.example.inetbank.credit.cardmanagement;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/credit-card-rewards/purchases")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMerchantPurchaseController {

    private final AdminMerchantPurchaseService purchaseService;

    public AdminMerchantPurchaseController(
            AdminMerchantPurchaseService purchaseService) {

        this.purchaseService = purchaseService;
    }

    @GetMapping
    public AdminMerchantPurchasePageResponse list(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return purchaseService.list(
                status,
                merchantId,
                page,
                size
        );
    }

    @GetMapping("/{merchantPurchaseId}")
    public AdminMerchantPurchaseResponse detail(
            @PathVariable Long merchantPurchaseId) {

        return purchaseService.detail(merchantPurchaseId);
    }
}