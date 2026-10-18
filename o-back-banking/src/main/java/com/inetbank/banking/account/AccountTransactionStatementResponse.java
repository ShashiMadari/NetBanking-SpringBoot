package com.inetbank.banking.account;

import java.time.LocalDate;
import java.util.List;

public record AccountTransactionStatementResponse(
        Long accountId,
        StatementPeriod period,
        LocalDate fromDate,
        LocalDate toDate,
        int page,
        int pageSize,
        long totalTransactions,
        int totalPages,
        List<AccountStatementResponse> transactions) {
}