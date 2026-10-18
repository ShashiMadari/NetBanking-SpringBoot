package com.example.inetbank.forex.statement;

import java.time.LocalDate;
import java.util.List;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;

public record ForexTransactionStatementResponse(

        ForexStatementPeriod period,
        LocalDate statementFrom,
        LocalDate statementTo,
        int page,
        int pageSize,
        long totalTransactions,
        int totalPages,
        List<ForexTransactionResponse> transactions
) {
}