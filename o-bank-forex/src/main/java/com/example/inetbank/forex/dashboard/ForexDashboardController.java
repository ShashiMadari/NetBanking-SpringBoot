package com.example.inetbank.forex.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forex")
@PreAuthorize("hasRole('CUSTOMER')")
public class ForexDashboardController {

    private final ForexDashboardService dashboardService;

    public ForexDashboardController(
            ForexDashboardService dashboardService) {

        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ForexDashboardResponse getDashboard(
            Authentication authentication) {

        return dashboardService.getDashboard(
                Long.valueOf(authentication.getName())
        );
    }
}