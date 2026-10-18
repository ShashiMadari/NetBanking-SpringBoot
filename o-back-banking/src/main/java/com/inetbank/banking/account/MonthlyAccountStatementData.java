package com.inetbank.banking.account;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record MonthlyAccountStatementData(
        String customerName,
        Long accountId,
        String accountNumber,
        String accountType,
        String branchName,
        String ifscCode,
        String currency,
        String accountStatus,
        YearMonth statementMonth,
        BigDecimal availableBalance,
        BigDecimal bookBalance,
        List<AccountStatementResponse> transactions) {
}