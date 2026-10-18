package com.inetbank.banking.adminpayment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    public AdminPaymentController(
            AdminPaymentService adminPaymentService) {

        this.adminPaymentService = adminPaymentService;
    }

    @GetMapping
    public AdminPaymentPageResponse list(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return adminPaymentService.list(
                status,
                type,
                page,
                size
        );
    }

    @GetMapping("/{paymentOrderId}")
    public AdminPaymentResponse details(
            @PathVariable Long paymentOrderId) {

        return adminPaymentService.details(paymentOrderId);
    }
}