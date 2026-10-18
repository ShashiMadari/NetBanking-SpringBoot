package com.inetbank.banking.customerpayment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerPaymentController {

    private final CustomerPaymentService customerPaymentService;

    public CustomerPaymentController(
            CustomerPaymentService customerPaymentService) {

        this.customerPaymentService = customerPaymentService;
    }

    @GetMapping
    public CustomerPaymentPageResponse list(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        return customerPaymentService.list(
                userId,
                status,
                type,
                page,
                size
        );
    }

    @GetMapping("/{paymentOrderId}")
    public CustomerPaymentResponse details(
            @PathVariable Long paymentOrderId,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        return customerPaymentService.details(
                userId,
                paymentOrderId
        );
    }
}