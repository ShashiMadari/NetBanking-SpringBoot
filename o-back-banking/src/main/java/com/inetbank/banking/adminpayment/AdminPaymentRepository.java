package com.inetbank.banking.adminpayment;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminPaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminPaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count(String status, String type) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                  FROM payment_orders p
                 WHERE 1 = 1
                """);

        List<Object> parameters = filters(sql, status, type);

        Long count = jdbcTemplate.queryForObject(
                sql.toString(),
                Long.class,
                parameters.toArray()
        );

        return count == null ? 0 : count;
    }

    public List<AdminPaymentResponse> findPage(
            String status,
            String type,
            int page,
            int size) {

        StringBuilder sql = new StringBuilder(baseQuery());

        List<Object> parameters = filters(sql, status, type);

        sql.append("""
                 ORDER BY p.requested_at DESC,
                          p.payment_order_id DESC
                 OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """);

        parameters.add(page * size);
        parameters.add(size);

        return jdbcTemplate.query(
                sql.toString(),
                (row, index) -> map(row),
                parameters.toArray()
        );
    }

    public Optional<AdminPaymentResponse> findById(
            Long paymentOrderId) {

        return jdbcTemplate.query(
                baseQuery() + """
                         AND p.payment_order_id = ?
                        """,
                (row, index) -> map(row),
                paymentOrderId
        ).stream().findFirst();
    }

    private String baseQuery() {
        return """
                SELECT p.payment_order_id,
                       p.reference_number,
                       p.payment_type,
                       p.amount,
                       p.status,
                       p.beneficiary_id,
                       p.biller_id,
                       p.consumer_reference,
                       p.requested_at,
                       p.completed_at,
                       p.reversal_reference_number,
                       p.reversal_reason,
                       p.reversed_at,
                       p.reversed_by_user_id,

                       a.account_id AS source_account_id,
                       a.account_number AS source_account_number,
                       a.user_id AS customer_user_id,

                       CAST(NULL AS VARCHAR2(120)) AS customer_name,
                       CAST(NULL AS VARCHAR2(120)) AS customer_email,

                       ben.beneficiary_name,
                       ben.beneficiary_account_number,

                       bil.biller_name
                  FROM payment_orders p
                  JOIN accounts a
                    ON a.account_id = p.source_account_id
             LEFT JOIN beneficiaries ben
                    ON ben.beneficiary_id = p.beneficiary_id
             LEFT JOIN billers bil
                    ON bil.biller_id = p.biller_id
                 WHERE 1 = 1
                """;
    }

    private List<Object> filters(
            StringBuilder sql,
            String status,
            String type) {

        List<Object> parameters = new ArrayList<>();

        if (!"ALL".equals(status)) {
            sql.append(" AND p.status = ? ");
            parameters.add(status);
        }

        if (!"ALL".equals(type)) {
            sql.append(" AND p.payment_type = ? ");
            parameters.add(type);
        }

        return parameters;
    }

    private AdminPaymentResponse map(
            java.sql.ResultSet row)
            throws java.sql.SQLException {

        String paymentStatus = row.getString("status");

        return new AdminPaymentResponse(
                row.getLong("payment_order_id"),
                row.getString("reference_number"),
                row.getString("payment_type"),
                row.getBigDecimal("amount"),
                paymentStatus,

                row.getLong("source_account_id"),
                row.getString("source_account_number"),

                row.getLong("customer_user_id"),
                row.getString("customer_name"),
                row.getString("customer_email"),

                number(row, "beneficiary_id"),
                row.getString("beneficiary_name"),
                row.getString("beneficiary_account_number"),

                number(row, "biller_id"),
                row.getString("biller_name"),
                row.getString("consumer_reference"),

                dateTime(row.getTimestamp("requested_at")),
                dateTime(row.getTimestamp("completed_at")),

                row.getString("reversal_reference_number"),
                row.getString("reversal_reason"),
                dateTime(row.getTimestamp("reversed_at")),
                number(row, "reversed_by_user_id"),

                "SUCCESS".equals(paymentStatus)
        );
    }

    private Long number(
            java.sql.ResultSet row,
            String column) throws java.sql.SQLException {

        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private java.time.LocalDateTime dateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}