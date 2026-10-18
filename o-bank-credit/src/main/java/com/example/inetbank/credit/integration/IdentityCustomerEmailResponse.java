package com.example.inetbank.credit.integration;

public record IdentityCustomerEmailResponse(
        Long userId,
        String email,
        String fullName
) {
}