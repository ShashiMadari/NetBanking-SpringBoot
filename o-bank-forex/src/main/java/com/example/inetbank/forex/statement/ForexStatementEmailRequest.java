package com.example.inetbank.forex.statement;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record ForexStatementEmailRequest(

        @NotNull
        ForexStatementPeriod period,

        LocalDate from,

        LocalDate to
) {
}