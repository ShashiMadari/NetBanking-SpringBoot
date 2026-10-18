package com.example.inetbank.credit.loan;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoanEmiScheduleRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoanEmiScheduleRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LoanEmiScheduleResponse> findByLoanId(
            Long loanId) {

        return jdbcTemplate.query("""
                SELECT loan_emi_schedule_id,
                       installment_number,
                       due_date,
                       opening_principal,
                       emi_amount,
                       principal_component,
                       interest_component,
                       closing_principal,
                       status,
                       paid_at
                  FROM loan_emi_schedules
                 WHERE loan_id = ?
                 ORDER BY installment_number
                """,
                (row, index) -> {
                    Timestamp paidAt = row.getTimestamp("paid_at");

                    return new LoanEmiScheduleResponse(
                            row.getLong("loan_emi_schedule_id"),
                            row.getInt("installment_number"),
                            row.getDate("due_date").toLocalDate(),
                            row.getBigDecimal("opening_principal"),
                            row.getBigDecimal("emi_amount"),
                            row.getBigDecimal("principal_component"),
                            row.getBigDecimal("interest_component"),
                            row.getBigDecimal("closing_principal"),
                            row.getString("status"),
                            paidAt == null
                                    ? null
                                    : paidAt.toLocalDateTime()
                    );
                },
                loanId
        );
    }
}