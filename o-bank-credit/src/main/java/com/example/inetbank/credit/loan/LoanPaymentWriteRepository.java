package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoanPaymentWriteRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoanPaymentWriteRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<LoanPaymentLoan> lockActiveLoan(
            Long loanId,
            Long customerUserId) {

        return jdbcTemplate.query("""
                SELECT loan_id,
                       user_id,
                       loan_account_number,
                       outstanding_amount,
                       installments_paid,
                       tenure_months,
                       status
                  FROM loans
                 WHERE loan_id = ?
                   AND user_id = ?
                 FOR UPDATE
                """,
                (row, index) -> new LoanPaymentLoan(
                        row.getLong("loan_id"),
                        row.getLong("user_id"),
                        row.getString("loan_account_number"),
                        row.getBigDecimal("outstanding_amount"),
                        row.getInt("installments_paid"),
                        row.getInt("tenure_months"),
                        row.getString("status")
                ),
                loanId,
                customerUserId
        ).stream().findFirst();
    }

    public Optional<DueEmiSchedule> lockNextDueEmi(
            Long loanId) {

        return jdbcTemplate.query("""
                SELECT loan_emi_schedule_id,
                       installment_number,
                       due_date,
                       emi_amount,
                       principal_component,
                       interest_component,
                       closing_principal,
                       status
                  FROM loan_emi_schedules
                 WHERE loan_id = ?
                   AND status = 'DUE'
                 ORDER BY installment_number
                 FOR UPDATE
                """,
                (row, index) -> new DueEmiSchedule(
                        row.getLong("loan_emi_schedule_id"),
                        row.getInt("installment_number"),
                        row.getDate("due_date").toLocalDate(),
                        row.getBigDecimal("emi_amount"),
                        row.getBigDecimal("principal_component"),
                        row.getBigDecimal("interest_component"),
                        row.getBigDecimal("closing_principal"),
                        row.getString("status")
                ),
                loanId
        ).stream().findFirst();
    }

    public Long createRepayment(
            Long paymentOrderId,
            Long loanId,
            Long sourceAccountId,
            String repaymentType,
            BigDecimal amount,
            BigDecimal outstandingBefore,
            BigDecimal outstandingAfter,
            BigDecimal principalComponent,
            BigDecimal interestComponent,
            String referenceNumber) {

        jdbcTemplate.update("""
                INSERT INTO loan_repayments (
                    loan_payment_order_id,
                    loan_id,
                    source_account_id,
                    repayment_type,
                    amount,
                    outstanding_before,
                    outstanding_after,
                    principal_component,
                    interest_component,
                    reference_number,
                    payment_date
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP)
                """,
                paymentOrderId,
                loanId,
                sourceAccountId,
                repaymentType,
                amount,
                outstandingBefore,
                outstandingAfter,
                principalComponent,
                interestComponent,
                referenceNumber
        );

        return jdbcTemplate.queryForObject("""
                SELECT loan_repayment_id
                  FROM loan_repayments
                 WHERE loan_payment_order_id = ?
                """,
                Long.class,
                paymentOrderId
        );
    }

    public void completeEmiPayment(
            Long loanId,
            Long customerUserId,
            DueEmiSchedule schedule,
            Long repaymentId) {

        int scheduleRows = jdbcTemplate.update("""
                UPDATE loan_emi_schedules
                   SET status = 'PAID',
                       paid_at = SYSTIMESTAMP,
                       loan_repayment_id = ?
                 WHERE loan_emi_schedule_id = ?
                   AND status = 'DUE'
                """,
                repaymentId,
                schedule.scheduleId()
        );

        if (scheduleRows != 1) {
            throw new IllegalStateException(
                    "The EMI schedule changed before payment completion."
            );
        }

        LocalDate nextDueDate = findNextDueDate(loanId);
        BigDecimal outstandingAfter =
                schedule.closingPrincipal();

        int loanRows = jdbcTemplate.update("""
                UPDATE loans
                   SET outstanding_amount = ?,
                       installments_paid = installments_paid + 1,
                       next_due_date = ?,
                       status = CASE
                           WHEN ? = 0 THEN 'CLOSED'
                           ELSE 'ACTIVE'
                       END
                 WHERE loan_id = ?
                   AND user_id = ?
                   AND status = 'ACTIVE'
                """,
                outstandingAfter,
                nextDueDate == null
                        ? null
                        : Date.valueOf(nextDueDate),
                outstandingAfter,
                loanId,
                customerUserId
        );

        if (loanRows != 1) {
            throw new IllegalStateException(
                    "Loan changed before payment completion."
            );
        }
    }

    public void completePartPayment(
            Long loanId,
            Long customerUserId,
            BigDecimal outstandingAfter) {

        int loanRows = jdbcTemplate.update("""
                UPDATE loans
                   SET outstanding_amount = ?
                 WHERE loan_id = ?
                   AND user_id = ?
                   AND status = 'ACTIVE'
                """,
                outstandingAfter,
                loanId,
                customerUserId
        );

        if (loanRows != 1) {
            throw new IllegalStateException(
                    "Loan changed before part-payment completion."
            );
        }

        jdbcTemplate.update("""
                BEGIN
                    pkg_loan_schedule.recalculate_remaining_schedule(
                        ?,
                        ?
                    );
                END;
                """,
                loanId,
                outstandingAfter
        );
    }

    public void completeForeclosure(
            Long loanId,
            Long customerUserId) {

        jdbcTemplate.update("""
                UPDATE loan_emi_schedules
                   SET status = 'SKIPPED'
                 WHERE loan_id = ?
                   AND status IN ('DUE', 'OVERDUE')
                """,
                loanId
        );

        int loanRows = jdbcTemplate.update("""
                UPDATE loans
                   SET outstanding_amount = 0,
                       next_due_date = NULL,
                       status = 'CLOSED'
                 WHERE loan_id = ?
                   AND user_id = ?
                   AND status = 'ACTIVE'
                """,
                loanId,
                customerUserId
        );

        if (loanRows != 1) {
            throw new IllegalStateException(
                    "Loan changed before foreclosure completion."
            );
        }
    }

    private LocalDate findNextDueDate(Long loanId) {

        return jdbcTemplate.query("""
                SELECT MIN(due_date) AS next_due_date
                  FROM loan_emi_schedules
                 WHERE loan_id = ?
                   AND status = 'DUE'
                """,
                (row, index) -> {
                    Date date = row.getDate("next_due_date");

                    return date == null
                            ? null
                            : date.toLocalDate();
                },
                loanId
        ).stream().findFirst().orElse(null);
    }

    public record LoanPaymentLoan(
            Long loanId,
            Long customerUserId,
            String loanAccountNumber,
            BigDecimal outstandingAmount,
            int installmentsPaid,
            int tenureMonths,
            String status) {
    }

    public record DueEmiSchedule(
            Long scheduleId,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal emiAmount,
            BigDecimal principalComponent,
            BigDecimal interestComponent,
            BigDecimal closingPrincipal,
            String status) {
    }
}