package com.example.inetbank.credit.loan;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoanService {

    private final LoanRepository loans;
    private final LoanEmiScheduleRepository emiSchedules;
    private final LoanRepaymentRepository repayments;

    public LoanService(
            LoanRepository loans,
            LoanEmiScheduleRepository emiSchedules,
            LoanRepaymentRepository repayments) {

        this.loans = loans;
        this.emiSchedules = emiSchedules;
        this.repayments = repayments;
    }

    @Transactional(readOnly = true)
    public List<LoanOverviewResponse> list(
            Long customerUserId) {

        return loans.findByUserId(customerUserId);
    }

    @Transactional(readOnly = true)
    public LoanOverviewResponse details(
            Long customerUserId,
            Long loanId) {

        return ownedLoan(customerUserId, loanId);
    }

    @Transactional(readOnly = true)
    public List<LoanEmiScheduleResponse> emiSchedule(
            Long customerUserId,
            Long loanId) {

        ownedLoan(customerUserId, loanId);

        return emiSchedules.findByLoanId(loanId);
    }

    @Transactional(readOnly = true)
    public List<LoanRepaymentResponse> repaymentHistory(
            Long customerUserId,
            Long loanId) {

        ownedLoan(customerUserId, loanId);

        return repayments.findByLoanId(loanId);
    }

    private LoanOverviewResponse ownedLoan(
            Long customerUserId,
            Long loanId) {

        return loans.findByIdAndUserId(loanId, customerUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Loan was not found for this customer."
                ));
    }
}