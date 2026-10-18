package com.inetbank.banking.integration;

public record IdentityCustomerContactResponse(
        Long userId,
        String email,
        String fullName) {
}