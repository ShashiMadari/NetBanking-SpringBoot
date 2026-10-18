package com.inetbank.banking.billpayment;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billers")
@PreAuthorize("hasRole('CUSTOMER')")
public class BillerController {

    private final BillerService billerService;

    BillerController(BillerService billerService) {
        this.billerService = billerService;
    }

    @GetMapping
    List<BillerResponse> activeBillers() {
        return billerService.activeBillers();
    }
}