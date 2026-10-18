package com.inetbank.banking.account;

public record AccountStatusResponse(
        Long accountId,
        String status,
        String message) {
}