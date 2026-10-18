package com.inetbank.banking.dashboard;

import java.math.BigDecimal;

public record DashboardAccountResponse(
        Long accountId,
        String accountNumber,
        String accountType,
        BigDecimal availableBalance,
        BigDecimal bookBalance,
        BigDecimal lienAmount,
        String currency,
        String status) {
}