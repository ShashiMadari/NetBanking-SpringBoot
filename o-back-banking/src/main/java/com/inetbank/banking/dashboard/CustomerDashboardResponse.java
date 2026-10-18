package com.inetbank.banking.dashboard;

import java.util.List;

public record CustomerDashboardResponse(
        Long userId,
        String fullName,
        String email,
        List<DashboardAccountResponse> accounts) {
}