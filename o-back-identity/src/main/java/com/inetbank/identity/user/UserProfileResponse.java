package com.inetbank.identity.user;

public record UserProfileResponse(
        String fullName,
        String email,
        String phone,
        String role,
        String status,
        String netBankingStatus) {
}