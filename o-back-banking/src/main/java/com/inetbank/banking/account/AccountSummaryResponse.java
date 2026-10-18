package com.inetbank.banking.account;

import java.math.BigDecimal;

public record AccountSummaryResponse(
        Long accountId,
        String accountNumber,
        String accountType,
        BigDecimal bookBalance,
        BigDecimal availableBalance,
        BigDecimal lienAmount,
        String currency,
        String status,
        String branchName,
        String ifscCode
) {
}