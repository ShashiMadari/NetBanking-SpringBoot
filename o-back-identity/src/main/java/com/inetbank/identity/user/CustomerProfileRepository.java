package com.inetbank.identity.user;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerProfileRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserProfileResponse> findProfileByEmail(
            String email) {

        return jdbcTemplate.query("""
                SELECT full_name,
                       email,
                       phone,
                       role,
                       status,
                       net_banking_status
                  FROM users
                 WHERE LOWER(email) = LOWER(?)
                """,
                (row, index) -> new UserProfileResponse(
                        row.getString("full_name"),
                        row.getString("email"),
                        row.getString("phone"),
                        row.getString("role"),
                        row.getString("status"),
                        row.getString("net_banking_status")
                ),
                email
        ).stream().findFirst();
    }
}