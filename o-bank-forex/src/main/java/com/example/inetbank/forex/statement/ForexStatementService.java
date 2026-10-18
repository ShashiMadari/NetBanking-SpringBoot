package com.example.inetbank.forex.statement;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;

@Service
public class ForexStatementService {

    private final ForexStatementRepository repository;

    public ForexStatementService(
            ForexStatementRepository repository) {

        this.repository = repository;
    }

    public ForexTransactionStatementResponse getStatement(
            Long userId,
            ForexStatementPeriod period,
            LocalDate requestedFrom,
            LocalDate requestedTo,
            int page,
            int pageSize) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page cannot be negative."
            );
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "pageSize must be between 1 and 100."
            );
        }

        StatementWindow window = resolveWindow(
                period,
                requestedFrom,
                requestedTo
        );

        long total = repository.countTransactions(
                userId,
                window.from(),
                window.to()
        );

        List<ForexTransactionResponse> transactions =
                repository.findTransactions(
                        userId,
                        window.from(),
                        window.to(),
                        page,
                        pageSize
                );

        int totalPages = (int) Math.ceil(
                (double) total / pageSize
        );

        return new ForexTransactionStatementResponse(
                window.period(),
                window.from(),
                window.to(),
                page,
                pageSize,
                total,
                totalPages,
                transactions
        );
    }

    private StatementWindow resolveWindow(
            ForexStatementPeriod requestedPeriod,
            LocalDate requestedFrom,
            LocalDate requestedTo) {

        ForexStatementPeriod period = requestedPeriod == null
                ? ForexStatementPeriod.RECENT
                : requestedPeriod;

        LocalDate today = LocalDate.now();

        return switch (period) {
            case RECENT -> new StatementWindow(
                    period,
                    today.minusDays(30),
                    today
            );

            case THREE_MONTHS -> new StatementWindow(
                    period,
                    today.minusMonths(3),
                    today
            );

            case SIX_MONTHS -> new StatementWindow(
                    period,
                    today.minusMonths(6),
                    today
            );

            case FISCAL_YEAR -> {
                LocalDate fiscalYearStart =
                        today.getMonthValue() >= Month.APRIL.getValue()
                                ? LocalDate.of(
                                        today.getYear(),
                                        Month.APRIL,
                                        1
                                )
                                : LocalDate.of(
                                        today.getYear() - 1,
                                        Month.APRIL,
                                        1
                                );

                yield new StatementWindow(
                        period,
                        fiscalYearStart,
                        today
                );
            }

            case CUSTOM -> {
                if (requestedFrom == null || requestedTo == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "CUSTOM statement requires from and to dates."
                    );
                }

                if (requestedFrom.isAfter(requestedTo)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "from date cannot be after to date."
                    );
                }

                if (requestedTo.isAfter(today)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "A statement cannot include future dates."
                    );
                }

                if (requestedFrom.plusDays(366)
                        .isBefore(requestedTo)) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "CUSTOM statement can cover at most 366 days."
                    );
                }

                yield new StatementWindow(
                        period,
                        requestedFrom,
                        requestedTo
                );
            }
        };
    }

    private record StatementWindow(
            ForexStatementPeriod period,
            LocalDate from,
            LocalDate to
    ) {
    }
}