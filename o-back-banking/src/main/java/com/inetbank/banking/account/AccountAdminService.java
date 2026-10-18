package com.inetbank.banking.account;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.inetbank.banking.integration.IdentityNotificationClient;
import com.inetbank.banking.integration.IdentitySystemNotificationClient;

@Service
public class AccountAdminService {

    private final AccountAdminRepository accounts;
    private final IdentityNotificationClient identityNotificationClient;
    private final IdentitySystemNotificationClient identitySystemNotificationClient;

    public AccountAdminService(
            AccountAdminRepository accounts,
            IdentityNotificationClient identityNotificationClient,
            IdentitySystemNotificationClient identitySystemNotificationClient) {

        this.accounts = accounts;
        this.identityNotificationClient = identityNotificationClient;
        this.identitySystemNotificationClient =
                identitySystemNotificationClient;
    }

    @Transactional
    public AccountStatusResponse freeze(
            Long accountId,
            Long adminUserId,
            String bearerToken,
            String reason) {

        AccountAdminRepository.AccountAdminDetails account =
                account(accountId);

        if ("FROZEN".equals(account.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Account is already frozen."
            );
        }

        if ("CLOSED".equals(account.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A closed account cannot be frozen."
            );
        }

        if (!"ACTIVE".equals(account.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an active account can be frozen."
            );
        }

        String safeReason = cleanReason(reason);

        updateStatus(accountId, "ACTIVE", "FROZEN");

        identityNotificationClient.createAdminNotification(
                bearerToken,
                account.customerUserId(),
                "SECURITY",
                "Your account "
                        + account.accountNumber()
                        + " was frozen. Reason: "
                        + safeReason
        );

        identitySystemNotificationClient.createAuditEvent(
                adminUserId,
                "ACCOUNT_FROZEN",
                "ACCOUNT",
                String.valueOf(accountId)
        );

        return new AccountStatusResponse(
                accountId,
                "FROZEN",
                "Account frozen successfully."
        );
    }

    @Transactional
    public AccountStatusResponse unfreeze(
            Long accountId,
            Long adminUserId,
            String bearerToken,
            String reason) {

        AccountAdminRepository.AccountAdminDetails account =
                account(accountId);

        if ("ACTIVE".equals(account.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Account is already active."
            );
        }

        if ("CLOSED".equals(account.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A closed account cannot be unfrozen."
            );
        }

        if (!"FROZEN".equals(account.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a frozen account can be unfrozen."
            );
        }

        String safeReason = cleanReason(reason);

        updateStatus(accountId, "FROZEN", "ACTIVE");

        identityNotificationClient.createAdminNotification(
                bearerToken,
                account.customerUserId(),
                "SECURITY",
                "Your account "
                        + account.accountNumber()
                        + " was unfrozen. Reason: "
                        + safeReason
        );

        identitySystemNotificationClient.createAuditEvent(
                adminUserId,
                "ACCOUNT_UNFROZEN",
                "ACCOUNT",
                String.valueOf(accountId)
        );

        return new AccountStatusResponse(
                accountId,
                "ACTIVE",
                "Account unfrozen successfully."
        );
    }

    private AccountAdminRepository.AccountAdminDetails account(
            Long accountId) {

        return accounts.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account was not found."
                ));
    }

    private void updateStatus(
            Long accountId,
            String expectedStatus,
            String newStatus) {

        if (!accounts.updateStatus(
                accountId,
                expectedStatus,
                newStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Account status changed. Please refresh and try again."
            );
        }
    }

    private String cleanReason(String reason) {
        return reason == null
                ? ""
                : reason.trim().replaceAll("\\s+", " ");
    }
}