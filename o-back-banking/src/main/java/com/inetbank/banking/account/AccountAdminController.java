package com.inetbank.banking.account;

import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AccountAdminController {

    private final AccountAdminService accountAdminService;

    public AccountAdminController(
            AccountAdminService accountAdminService) {

        this.accountAdminService = accountAdminService;
    }

    @PatchMapping("/{accountId}/freeze")
    public AccountStatusResponse freeze(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountStatusChangeRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            Authentication authentication) {

        Long adminUserId = Long.valueOf(authentication.getName());

        return accountAdminService.freeze(
                accountId,
                adminUserId,
                bearerToken,
                request.reason()
        );
    }

    @PatchMapping("/{accountId}/unfreeze")
    public AccountStatusResponse unfreeze(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountStatusChangeRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            Authentication authentication) {

        Long adminUserId = Long.valueOf(authentication.getName());

        return accountAdminService.unfreeze(
                accountId,
                adminUserId,
                bearerToken,
                request.reason()
        );
    }
}