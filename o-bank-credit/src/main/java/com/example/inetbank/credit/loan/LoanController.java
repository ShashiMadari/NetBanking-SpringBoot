package com.example.inetbank.credit.loan;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
@PreAuthorize("hasRole('CUSTOMER')")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    public List<LoanOverviewResponse> list(
            Authentication authentication) {

        return loanService.list(
                Long.valueOf(authentication.getName())
        );
    }

    @GetMapping("/{loanId}")
    public LoanOverviewResponse details(
            @PathVariable Long loanId,
            Authentication authentication) {

        return loanService.details(
                Long.valueOf(authentication.getName()),
                loanId
        );
    }

    @GetMapping("/{loanId}/emi-schedule")
    public List<LoanEmiScheduleResponse> emiSchedule(
            @PathVariable Long loanId,
            Authentication authentication) {

        return loanService.emiSchedule(
                Long.valueOf(authentication.getName()),
                loanId
        );
    }

    @GetMapping("/{loanId}/repayments")
    public List<LoanRepaymentResponse> repaymentHistory(
            @PathVariable Long loanId,
            Authentication authentication) {

        return loanService.repaymentHistory(
                Long.valueOf(authentication.getName()),
                loanId
        );
    }
}