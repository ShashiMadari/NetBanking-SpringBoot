package com.example.inetbank.forex.beneficiary;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ForexBeneficiaryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForexBeneficiaryRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public Long create(
            Long userId,
            ForexBeneficiaryRequest request,
            String countryCode,
            String currencyCode,
            String swiftBic) {

        jdbcTemplate.update("""
                INSERT INTO forex_beneficiaries (
                    user_id,
                    beneficiary_name,
                    country_code,
                    currency_code,
                    swift_bic,
                    account_number,
                    status,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', SYSTIMESTAMP)
                """,
                userId,
                request.beneficiaryName().trim(),
                countryCode,
                currencyCode,
                swiftBic,
                request.accountNumber().trim()
        );

        return jdbcTemplate.queryForObject("""
                SELECT forex_beneficiary_id
                  FROM forex_beneficiaries
                 WHERE user_id = ?
                   AND account_number = ?
                 ORDER BY forex_beneficiary_id DESC
                 FETCH FIRST 1 ROWS ONLY
                """,
                Long.class,
                userId,
                request.accountNumber().trim()
        );
    }

    public ForexBeneficiaryResponse findByIdAndUserId(
            Long beneficiaryId,
            Long userId) {

        return jdbcTemplate.queryForObject("""
                SELECT forex_beneficiary_id,
                       beneficiary_name,
                       country_code,
                       currency_code,
                       swift_bic,
                       account_number,
                       status,
                       created_at
                  FROM forex_beneficiaries
                 WHERE forex_beneficiary_id = ?
                   AND user_id = ?
                """,
                (row, index) -> map(row),
                beneficiaryId,
                userId
        );
    }

    public List<ForexBeneficiaryResponse> findByUserId(
            Long userId) {

        return jdbcTemplate.query("""
                SELECT forex_beneficiary_id,
                       beneficiary_name,
                       country_code,
                       currency_code,
                       swift_bic,
                       account_number,
                       status,
                       created_at
                  FROM forex_beneficiaries
                 WHERE user_id = ?
                 ORDER BY forex_beneficiary_id DESC
                """,
                (row, index) -> map(row),
                userId
        );
    }

    private ForexBeneficiaryResponse map(
            java.sql.ResultSet row)
            throws java.sql.SQLException {

        String accountNumber = row.getString("account_number");

        String maskedAccountNumber =
                accountNumber.length() <= 4
                        ? "****"
                        : "****" + accountNumber.substring(
                                accountNumber.length() - 4
                        );

        return new ForexBeneficiaryResponse(
                row.getLong("forex_beneficiary_id"),
                row.getString("beneficiary_name"),
                row.getString("country_code"),
                row.getString("currency_code"),
                row.getString("swift_bic"),
                maskedAccountNumber,
                row.getString("status"),
                row.getTimestamp("created_at")
                        .toLocalDateTime()
        );
    }
}