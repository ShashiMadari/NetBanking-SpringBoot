package com.example.inetbank.credit.cardmanagement;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRewardMerchantRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminRewardMerchantRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RewardMerchantResponse> findAll() {

        return jdbcTemplate.query("""
                SELECT merchant_id,
                       merchant_code,
                       merchant_name,
                       merchant_category,
                       reward_eligible,
                       status
                  FROM merchants
                 ORDER BY merchant_name,
                          merchant_id
                """,
                (row, index) -> map(row)
        );
    }

    public Optional<RewardMerchantResponse> findById(
            Long merchantId) {

        return jdbcTemplate.query("""
                SELECT merchant_id,
                       merchant_code,
                       merchant_name,
                       merchant_category,
                       reward_eligible,
                       status
                  FROM merchants
                 WHERE merchant_id = ?
                """,
                (row, index) -> map(row),
                merchantId
        ).stream().findFirst();
    }

    public Optional<RewardMerchantResponse> findByCode(
            String merchantCode) {

        return jdbcTemplate.query("""
                SELECT merchant_id,
                       merchant_code,
                       merchant_name,
                       merchant_category,
                       reward_eligible,
                       status
                  FROM merchants
                 WHERE merchant_code = ?
                """,
                (row, index) -> map(row),
                merchantCode
        ).stream().findFirst();
    }

    public void create(
            String merchantCode,
            String merchantName,
            String merchantCategory,
            boolean rewardEligible) {

        jdbcTemplate.update("""
                INSERT INTO merchants (
                    merchant_code,
                    merchant_name,
                    merchant_category,
                    reward_eligible,
                    status,
                    created_at
                )
                VALUES (?, ?, ?, ?, 'ACTIVE', SYSTIMESTAMP)
                """,
                merchantCode,
                merchantName,
                merchantCategory,
                rewardEligible ? "Y" : "N"
        );
    }

    public boolean updateStatus(
            Long merchantId,
            String status) {

        return jdbcTemplate.update("""
                UPDATE merchants
                   SET status = ?
                 WHERE merchant_id = ?
                """,
                status,
                merchantId
        ) == 1;
    }

    public boolean updateRewardEligibility(
            Long merchantId,
            boolean rewardEligible) {

        return jdbcTemplate.update("""
                UPDATE merchants
                   SET reward_eligible = ?
                 WHERE merchant_id = ?
                """,
                rewardEligible ? "Y" : "N",
                merchantId
        ) == 1;
    }

    private RewardMerchantResponse map(
            java.sql.ResultSet row) throws java.sql.SQLException {

        return new RewardMerchantResponse(
                row.getLong("merchant_id"),
                row.getString("merchant_code"),
                row.getString("merchant_name"),
                row.getString("merchant_category"),
                "Y".equals(row.getString("reward_eligible")),
                row.getString("status")
        );
    }
}