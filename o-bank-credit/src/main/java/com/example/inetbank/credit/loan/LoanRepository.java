package com.example.inetbank.credit.loan;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoanRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LoanOverviewResponse> findByUserId(Long userId) {
        return jdbcTemplate.query("""
                SELECT loan_id,
                       loan_account_number,
                       loan_type,
                       loan_amount,
                       outstanding_amount,
                       interest_rate,
                       tenure_months,
                       installments_paid,
                       emi_amount,
                       next_due_date,
                       status
                  FROM loans
                 WHERE user_id = ?
                 ORDER BY created_at DESC
                """,
                (row, index) -> map(row),
                userId
        );
    }

    public Optional<LoanOverviewResponse> findByIdAndUserId(
            Long loanId,
            Long userId) {

        return jdbcTemplate.query("""
                SELECT loan_id,
                       loan_account_number,
                       loan_type,
                       loan_amount,
                       outstanding_amount,
                       interest_rate,
                       tenure_months,
                       installments_paid,
                       emi_amount,
                       next_due_date,
                       status
                  FROM loans
                 WHERE loan_id = ?
                   AND user_id = ?
                """,
                (row, index) -> map(row),
                loanId,
                userId
        ).stream().findFirst();
    }

    private LoanOverviewResponse map(
            java.sql.ResultSet row) throws java.sql.SQLException {

        java.sql.Date nextDueDate = row.getDate("next_due_date");

        return new LoanOverviewResponse(
                row.getLong("loan_id"),
                row.getString("loan_account_number"),
                row.getString("loan_type"),
                row.getBigDecimal("loan_amount"),
                row.getBigDecimal("outstanding_amount"),
                row.getBigDecimal("interest_rate"),
                row.getInt("tenure_months"),
                row.getInt("installments_paid"),
                row.getBigDecimal("emi_amount"),
                nextDueDate == null ? null : nextDueDate.toLocalDate(),
                row.getString("status")
        );
    }
}