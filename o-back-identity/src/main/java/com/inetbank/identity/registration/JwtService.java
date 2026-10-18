package com.inetbank.identity.registration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final long expirationSeconds;

    JwtService(@Value("${app.security.jwt-secret}") String base64Secret, @Value("${app.security.jwt-expiration-seconds:900}") long expirationSeconds) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationSeconds = expirationSeconds;
    }

    public String create(User user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(user.getEmail()).claim("role", user.getRole()).claim("userId", user.getId()).issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirationSeconds))).signWith(signingKey).compact();
    }

    public Claims parse(String token) { return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload(); }
    public long getExpirationSeconds() { return expirationSeconds; }
}
