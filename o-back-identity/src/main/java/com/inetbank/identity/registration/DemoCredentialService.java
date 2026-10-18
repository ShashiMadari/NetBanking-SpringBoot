package com.inetbank.identity.registration;

import java.security.SecureRandom;
import org.springframework.stereotype.Service;

@Service
public class DemoCredentialService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";

    public String demoPassword() {
        StringBuilder result = new StringBuilder("Db1!");
        for (int i = 0; i < 8; i++) result.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        return result.toString();
    }

    public String otp() { return String.format("%06d", RANDOM.nextInt(1_000_000)); }
}
