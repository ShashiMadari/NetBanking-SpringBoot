package com.inetbank.investment;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/investments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInvestmentSettlementController {

    private final InvestmentService service;

    public AdminInvestmentSettlementController(
            InvestmentService service) {

        this.service = service;
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders(
            Authentication authentication,
            @RequestParam(required = false) String status) {

        return service.adminOrders(
                authentication.getName(),
                status
        );
    }

    @GetMapping("/matured-fds")
    public List<Map<String, Object>> maturedFixedDeposits(Authentication authentication) {
        return service.maturedFixedDeposits(authentication.getName());
    }

    @GetMapping("/schedules")
    public List<Map<String, Object>> schedules(
            Authentication authentication) {

        return service.adminSchedules(
                authentication.getName()
        );
    }
}