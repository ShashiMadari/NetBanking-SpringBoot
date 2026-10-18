package com.example.inetbank.credit.loan;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public class LoanRepaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoanRepaymentRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LoanRepaymentResponse> findByLoanId(
            Long loanId) {

        return jdbcTemplate.query("""
                SELECT loan_repayment_id,
                       loan_payment_order_id,
                       source_account_id,
                       repayment_type,
                       amount,
                       outstanding_before,
                       outstanding_after,
                       principal_component,
                       interest_component,
                       reference_number,
                       payment_date
                  FROM loan_repayments
                 WHERE loan_id = ?
                 ORDER BY payment_date DESC,
                          loan_repayment_id DESC
                """,
                (row, index) -> {
                    Timestamp paymentDate =
                            row.getTimestamp("payment_date");

                    return new LoanRepaymentResponse(
                            row.getLong("loan_repayment_id"),
                            row.getLong("loan_payment_order_id"),
                            row.getLong("source_account_id"),
                            row.getString("repayment_type"),
                            row.getBigDecimal("amount"),
                            row.getBigDecimal("outstanding_before"),
                            row.getBigDecimal("outstanding_after"),
                            row.getBigDecimal("principal_component"),
                            row.getBigDecimal("interest_component"),
                            row.getString("reference_number"),
                            paymentDate.toLocalDateTime()
                    );
                },
                loanId
        );
    }

    public List<LoanMonthlyStatementEntryResponse> findMonthlyEntries(
            Long loanId,
            java.time.LocalDateTime from,
            java.time.LocalDateTime to) {

        return jdbcTemplate.query("""
                SELECT payment_date,
                    repayment_type,
                    reference_number,
                    amount,
                    principal_component,
                    interest_component,
                    outstanding_before,
                    outstanding_after
                FROM loan_repayments
                WHERE loan_id = ?
                AND payment_date >= ?
                AND payment_date < ?
                ORDER BY payment_date,
                        loan_repayment_id
                """,
                (row, index) -> {
                    java.sql.Timestamp paymentDate =
                            row.getTimestamp("payment_date");

                    return new LoanMonthlyStatementEntryResponse(
                            paymentDate.toLocalDateTime(),
                            row.getString("repayment_type"),
                            row.getString("reference_number"),
                            row.getBigDecimal("amount"),
                            row.getBigDecimal("principal_component"),
                            row.getBigDecimal("interest_component"),
                            row.getBigDecimal("outstanding_before"),
                            row.getBigDecimal("outstanding_after")
                    );
                },
                loanId,
                java.sql.Timestamp.valueOf(from),
                java.sql.Timestamp.valueOf(to)
        );
    }
}