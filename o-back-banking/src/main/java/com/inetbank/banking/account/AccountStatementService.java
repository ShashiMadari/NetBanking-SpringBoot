package com.inetbank.banking.account;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountStatementService {

    private final AccountStatementRepository statements;

    public AccountStatementService(
            AccountStatementRepository statements) {

        this.statements = statements;
    }

    @Transactional(readOnly = true)
    public List<AccountStatementResponse> getForCustomerAccount(
            Long accountId,
            Long userId) {

        ensureAccountOwnership(accountId, userId);

        return statements.findForCustomerAccount(
                accountId,
                userId
        );
    }

    @Transactional(readOnly = true)
    public AccountTransactionStatementResponse
            getStatementForCustomerAccount(
                    Long accountId,
                    Long userId,
                    StatementPeriod period,
                    LocalDate customFrom,
                    LocalDate customTo,
                    int page,
                    int size) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater."
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100."
            );
        }

        ensureAccountOwnership(accountId, userId);

        StatementPeriod selectedPeriod = period == null
                ? StatementPeriod.RECENT
                : period;

        DateRange range = resolveDateRange(
                selectedPeriod,
                customFrom,
                customTo
        );

        long totalTransactions = statements.countForCustomerAccount(
                accountId,
                userId,
                range == null ? null : range.from(),
                range == null ? null : range.to()
        );

        List<AccountStatementResponse> transactions =
                statements.findPageForCustomerAccount(
                        accountId,
                        userId,
                        range == null ? null : range.from(),
                        range == null ? null : range.to(),
                        page,
                        size
                );

        int totalPages = totalTransactions == 0
                ? 0
                : (int) Math.ceil(
                        (double) totalTransactions / size
                );

        return new AccountTransactionStatementResponse(
                accountId,
                selectedPeriod,
                range == null ? null : range.from(),
                range == null ? null : range.to(),
                page,
                size,
                totalTransactions,
                totalPages,
                transactions
        );
    }

    private void ensureAccountOwnership(
            Long accountId,
            Long userId) {

        if (!statements.accountBelongsToCustomer(
                accountId,
                userId
        )) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Account was not found."
            );
        }
    }

    private DateRange resolveDateRange(
            StatementPeriod period,
            LocalDate customFrom,
            LocalDate customTo) {

        LocalDate today = LocalDate.now();

        return switch (period) {
            case RECENT -> null;

            case THREE_MONTHS -> new DateRange(
                    today.minusMonths(3),
                    today
            );

            case SIX_MONTHS -> new DateRange(
                    today.minusMonths(6),
                    today
            );

            case FISCAL_YEAR -> {
                LocalDate start = today.getMonthValue()
                        >= Month.APRIL.getValue()
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

                yield new DateRange(start, today);
            }

            case CUSTOM -> customRange(
                    customFrom,
                    customTo,
                    today
            );
        };
    }

    private DateRange customRange(
            LocalDate from,
            LocalDate to,
            LocalDate today) {

        if (from == null || to == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CUSTOM period requires both from and to dates."
            );
        }

        if (from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "From date cannot be after to date."
            );
        }

        if (to.isAfter(today)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "To date cannot be in the future."
            );
        }

        return new DateRange(from, to);
    }

    private record DateRange(
            LocalDate from,
            LocalDate to) {
    }
}