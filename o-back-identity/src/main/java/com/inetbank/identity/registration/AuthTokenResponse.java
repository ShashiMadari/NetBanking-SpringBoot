package com.inetbank.identity.registration;

public record AuthTokenResponse(String token, String tokenType, long expiresInSeconds, String role, String message) {}
