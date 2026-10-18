package com.inetbank.banking.account;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/internal/investment/accounts")
public class InternalInvestmentDebitController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "INVESTMENT_PURCHASE",
            "TRADING_WALLET_FUND"
    );

    private final JdbcTemplate jdbc;
    private final String bankingApiKey;

    public InternalInvestmentDebitController(
            JdbcTemplate jdbc,
            @Value("${app.internal.banking-api-key}")
            String bankingApiKey) {

        this.jdbc = jdbc;
        this.bankingApiKey = bankingApiKey;
    }

    @PostMapping("/debit")
    @Transactional
    public DebitResponse debit(
            @RequestHeader(
                    value = "X-Internal-Api-Key",
                    defaultValue = ""
            )
            String suppliedApiKey,
            @Valid @RequestBody DebitRequest request) {

        validateInternalApiKey(suppliedApiKey);

        if (!ALLOWED_TYPES.contains(request.transactionType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported investment transaction type."
            );
        }

        ExistingTransaction existing = jdbc.query("""
                SELECT transaction_id, account_id, transaction_type,
                       entry_side, amount, status
                FROM transactions
                WHERE reference_number = ?
                """,
                (row, index) -> new ExistingTransaction(
                        row.getLong("transaction_id"),
                        row.getLong("account_id"),
                        row.getString("transaction_type"),
                        row.getString("entry_side"),
                        row.getBigDecimal("amount"),
                        row.getString("status")
                ),
                request.referenceNumber()
        ).stream().findFirst().orElse(null);

        if (existing != null) {
            boolean matches = existing.accountId()
                    .equals(request.accountId())
                    && existing.amount()
                    .compareTo(request.amount()) == 0
                    && existing.transactionType()
                    .equals(request.transactionType())
                    && "DEBIT".equals(existing.entrySide())
                    && "SUCCESS".equals(existing.status());

            if (!matches) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Reference number is already in use."
                );
            }

            return new DebitResponse(
                    existing.transactionId(),
                    request.referenceNumber(),
                    true
            );
        }

        int updated = jdbc.update("""
                UPDATE accounts
                SET book_balance = book_balance - ?,
                    available_balance = available_balance - ?
                WHERE account_id = ?
                  AND user_id = ?
                  AND status = 'ACTIVE'
                  AND book_balance >= ?
                  AND available_balance >= ?
                """,
                request.amount(),
                request.amount(),
                request.accountId(),
                request.userId(),
                request.amount(),
                request.amount()
        );

        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Source account is unavailable, inactive, or has insufficient balance."
            );
        }

        jdbc.update("""
                INSERT INTO transactions (
                    payment_order_id,
                    account_id,
                    transaction_type,
                    entry_side,
                    amount,
                    reference_number,
                    status,
                    transaction_date
                )
                VALUES (
                    NULL, ?, ?, 'DEBIT', ?, ?, 'SUCCESS', SYSTIMESTAMP
                )
                """,
                request.accountId(),
                request.transactionType(),
                request.amount(),
                request.referenceNumber()
        );

        Long transactionId = jdbc.queryForObject("""
                SELECT transaction_id
                FROM transactions
                WHERE reference_number = ?
                """,
                Long.class,
                request.referenceNumber()
        );

        return new DebitResponse(
                transactionId,
                request.referenceNumber(),
                false
        );
    }

    private void validateInternalApiKey(String suppliedApiKey) {
        if (!MessageDigest.isEqual(
                bankingApiKey.getBytes(StandardCharsets.UTF_8),
                suppliedApiKey.getBytes(StandardCharsets.UTF_8))) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid internal API key."
            );
        }
    }

    public record DebitRequest(
            @NotNull Long userId,
            @NotNull Long accountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String transactionType,
            @NotBlank String referenceNumber
    ) {
    }

    public record DebitResponse(
            Long transactionId,
            String referenceNumber,
            boolean alreadyProcessed
    ) {
    }

    private record ExistingTransaction(
            Long transactionId,
            Long accountId,
            String transactionType,
            String entrySide,
            BigDecimal amount,
            String status
    ) {
    }
}