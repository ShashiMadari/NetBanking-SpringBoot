package com.example.inetbank.forex.dashboard;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForexDashboardService {

    private final ForexDashboardRepository repository;

    public ForexDashboardService(
            ForexDashboardRepository repository) {

        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ForexDashboardResponse getDashboard(
            Long userId) {

        return new ForexDashboardResponse(
                repository.findWallets(userId),
                repository.findRecentTransactions(userId)
        );
    }
}