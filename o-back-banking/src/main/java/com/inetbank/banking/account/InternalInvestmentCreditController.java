package com.inetbank.banking.account;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
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
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/internal/investment/accounts")
public class InternalInvestmentCreditController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "INVESTMENT_REDEMPTION",
            "TRADING_WALLET_WITHDRAWAL"
    );

    private final JdbcTemplate jdbc;
    private final String bankingApiKey;

    public InternalInvestmentCreditController(
            JdbcTemplate jdbc,
            @Value("${app.internal.banking-api-key}") String bankingApiKey) {

        this.jdbc = jdbc;
        this.bankingApiKey = bankingApiKey;
    }

    @PostMapping("/credit")
    @Transactional
    public CreditResponse credit(
            @RequestHeader(value = "X-Internal-Api-Key", defaultValue = "")
            String suppliedApiKey,
            @Valid @RequestBody CreditRequest request) {

        if (!MessageDigest.isEqual(
                bankingApiKey.getBytes(StandardCharsets.UTF_8),
                suppliedApiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Invalid internal API key.");
        }

        if (!ALLOWED_TYPES.contains(request.transactionType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported Investment credit transaction type.");
        }

        // Serialize credits to this account and validate ownership even on replay.
        List<String> accountStates = jdbc.query("""
                SELECT status
                FROM accounts
                WHERE account_id = ?
                  AND user_id = ?
                FOR UPDATE
                """,
                (row, index) -> row.getString("status"),
                request.accountId(), request.userId());

        if (accountStates.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Destination account was not found for this customer.");
        }

        List<ExistingTransaction> existingRows = jdbc.query("""
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
                        row.getString("status")),
                request.referenceNumber());

        if (!existingRows.isEmpty()) {
            ExistingTransaction existing = existingRows.get(0);
            boolean matches = existingRows.size() == 1
                    && request.accountId().equals(existing.accountId())
                    && request.transactionType().equals(existing.transactionType())
                    && "CREDIT".equals(existing.entrySide())
                    && "SUCCESS".equals(existing.status())
                    && request.amount().compareTo(existing.amount()) == 0;

            if (!matches) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Reference number is already in use.");
            }

            return new CreditResponse(
                    existing.transactionId(), true, request.referenceNumber());
        }

        if (!"ACTIVE".equals(accountStates.get(0))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Destination account is inactive.");
        }

        int updated = jdbc.update("""
                UPDATE accounts
                SET book_balance = book_balance + ?,
                    available_balance = available_balance + ?
                WHERE account_id = ?
                  AND user_id = ?
                  AND status = 'ACTIVE'
                """,
                request.amount(), request.amount(),
                request.accountId(), request.userId());

        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Destination account could not be credited.");
        }

        // Requires a unique constraint on transactions.reference_number.
        // Any insert failure rolls the account update back in this transaction.
        jdbc.update("""
                INSERT INTO transactions (
                    payment_order_id, account_id, transaction_type,
                    entry_side, amount, reference_number,
                    status, transaction_date
                )
                VALUES (NULL, ?, ?, 'CREDIT', ?, ?, 'SUCCESS', SYSTIMESTAMP)
                """,
                request.accountId(), request.transactionType(),
                request.amount(), request.referenceNumber());

        Long transactionId = jdbc.queryForObject("""
                SELECT transaction_id
                FROM transactions
                WHERE reference_number = ?
                """, Long.class, request.referenceNumber());

        return new CreditResponse(transactionId, true, request.referenceNumber());
    }

    public record CreditRequest(
            @NotNull @Positive Long userId,
            @NotNull @Positive Long accountId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @NotBlank @Size(max = 30) String transactionType,
            @NotBlank @Size(max = 40) String referenceNumber
    ) {
    }

    public record CreditResponse(
            Long transactionId,
            boolean credited,
            String referenceNumber
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
