package com.example.inetbank.forex.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminForexKycRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminForexKycRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(
            Long customerUserId,
            Long adminUserId,
            String status) {

        jdbcTemplate.update("""
                MERGE INTO user_kyc target
                USING (
                    SELECT ? AS user_id
                    FROM dual
                ) source
                ON (target.user_id = source.user_id)
                WHEN MATCHED THEN
                    UPDATE SET
                        kyc_status = ?,
                        updated_by = ?,
                        updated_at = SYSTIMESTAMP
                WHEN NOT MATCHED THEN
                    INSERT (
                        user_id,
                        kyc_status,
                        updated_by,
                        updated_at
                    )
                    VALUES (
                        source.user_id,
                        ?,
                        ?,
                        SYSTIMESTAMP
                    )
                """,
                customerUserId,
                status,
                adminUserId,
                status,
                adminUserId
        );
    }
}