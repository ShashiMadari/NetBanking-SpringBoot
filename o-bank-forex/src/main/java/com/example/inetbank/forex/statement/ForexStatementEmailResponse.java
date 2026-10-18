package com.example.inetbank.forex.statement;

import java.time.LocalDate;

public record ForexStatementEmailResponse(

        String message,
        long transactionCount,
        LocalDate statementFrom,
        LocalDate statementTo
) {
}