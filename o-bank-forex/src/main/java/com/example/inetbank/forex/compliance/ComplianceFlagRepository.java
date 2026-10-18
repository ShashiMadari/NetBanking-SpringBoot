package com.example.inetbank.forex.compliance;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ComplianceFlagRepository {

    private final JdbcTemplate jdbcTemplate;

    public ComplianceFlagRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createHighValueFlag(
            Long forexTransactionId,
            String reason) {

        jdbcTemplate.update("""
                INSERT INTO compliance_flags (
                    forex_transaction_id,
                    rule_code,
                    reason,
                    status,
                    created_at
                )
                VALUES (
                    ?,
                    'HIGH_VALUE_TRANSACTION',
                    ?,
                    'OPEN',
                    SYSTIMESTAMP
                )
                """,
                forexTransactionId,
                reason
        );
    }

    public List<ComplianceFlagResponse> findAll(
            String status) {

        if (status == null || status.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT compliance_flag_id,
                           forex_transaction_id,
                           rule_code,
                           reason,
                           status,
                           created_at,
                           reviewed_by,
                           reviewed_at
                      FROM compliance_flags
                     ORDER BY created_at DESC
                     FETCH FIRST 200 ROWS ONLY
                    """,
                    (row, index) -> map(row)
            );
        }

        return jdbcTemplate.query("""
                SELECT compliance_flag_id,
                       forex_transaction_id,
                       rule_code,
                       reason,
                       status,
                       created_at,
                       reviewed_by,
                       reviewed_at
                  FROM compliance_flags
                 WHERE status = ?
                 ORDER BY created_at DESC
                 FETCH FIRST 200 ROWS ONLY
                """,
                (row, index) -> map(row),
                status
        );
    }

    public boolean review(
            Long complianceFlagId,
            Long adminUserId,
            String status) {

        return jdbcTemplate.update("""
                UPDATE compliance_flags
                   SET status = ?,
                       reviewed_by = ?,
                       reviewed_at = SYSTIMESTAMP
                 WHERE compliance_flag_id = ?
                   AND status = 'OPEN'
                """,
                status,
                adminUserId,
                complianceFlagId
        ) == 1;
    }

    public Optional<ComplianceFlagResponse> findById(
            Long complianceFlagId) {

        return jdbcTemplate.query("""
                SELECT compliance_flag_id,
                       forex_transaction_id,
                       rule_code,
                       reason,
                       status,
                       created_at,
                       reviewed_by,
                       reviewed_at
                  FROM compliance_flags
                 WHERE compliance_flag_id = ?
                """,
                (row, index) -> map(row),
                complianceFlagId
        ).stream().findFirst();
    }

    private ComplianceFlagResponse map(
            java.sql.ResultSet row)
            throws java.sql.SQLException {

        Number reviewedBy = (Number) row.getObject(
                "reviewed_by"
        );

        java.sql.Timestamp reviewedAt = row.getTimestamp(
                "reviewed_at"
        );

        return new ComplianceFlagResponse(
                row.getLong("compliance_flag_id"),
                row.getLong("forex_transaction_id"),
                row.getString("rule_code"),
                row.getString("reason"),
                row.getString("status"),
                row.getTimestamp("created_at")
                        .toLocalDateTime(),
                reviewedBy == null
                        ? null
                        : reviewedBy.longValue(),
                reviewedAt == null
                        ? null
                        : reviewedAt.toLocalDateTime()
        );
    }
}