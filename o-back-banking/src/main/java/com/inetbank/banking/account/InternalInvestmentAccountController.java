package com.inetbank.banking.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts")
public class InternalInvestmentAccountController {

    private final JdbcTemplate jdbc;
    private final String bankingApiKey;

    public InternalInvestmentAccountController(
            JdbcTemplate jdbc,
            @Value("${app.internal.banking-api-key}")
            String bankingApiKey) {

        this.jdbc = jdbc;
        this.bankingApiKey = bankingApiKey;
    }

    @GetMapping("/{accountId}/users/{userId}/active")
    public ActiveAccountResponse hasActiveOwnedAccount(
            @PathVariable("accountId") Long accountId,
            @PathVariable("userId") Long userId,
            @RequestHeader(
                    value = "X-Internal-Api-Key",
                    required = false
            ) String suppliedApiKey) {

        validateInternalApiKey(suppliedApiKey);

        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM accounts
                WHERE account_id = ?
                  AND user_id = ?
                  AND UPPER(status) = 'ACTIVE'
                """,
                Integer.class,
                accountId,
                userId
        );

        return new ActiveAccountResponse(
                count != null && count > 0
        );
    }

    /**
     * Used by Identity during first-time net-banking registration. Identity
     * knows the customer id but must not query the Banking schema directly.
     */
    @GetMapping("/{userId}/active")
    public ActiveAccountResponse hasActiveAccount(
            @PathVariable("userId") Long userId,
            @RequestHeader(
                    value = "X-Internal-Api-Key",
                    required = false
            ) String suppliedApiKey) {

        validateInternalApiKey(suppliedApiKey);

        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM accounts
                WHERE user_id = ?
                  AND UPPER(status) = 'ACTIVE'
                """,
                Integer.class,
                userId
        );

        return new ActiveAccountResponse(count != null && count > 0);
    }

    private void validateInternalApiKey(String suppliedApiKey) {
        if (suppliedApiKey == null
                || !MessageDigest.isEqual(
                        bankingApiKey.getBytes(StandardCharsets.UTF_8),
                        suppliedApiKey.getBytes(StandardCharsets.UTF_8))) {

            throw new IllegalArgumentException(
                    "Invalid internal API key."
            );
        }
    }

    public record ActiveAccountResponse(boolean active) {
    }
}
