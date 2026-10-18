package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoanMonthlyStatementService {

    private final LoanRepository loans;
    private final LoanRepaymentRepository repayments;
    private final LoanStatementPdfService pdfService;
    private final LoanStatementEmailService emailService;

    public LoanMonthlyStatementService(
            LoanRepository loans,
            LoanRepaymentRepository repayments,
            LoanStatementPdfService pdfService,
            LoanStatementEmailService emailService) {

        this.loans = loans;
        this.repayments = repayments;
        this.pdfService = pdfService;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public LoanMonthlyStatementResponse getStatement(
            Long customerUserId,
            Long loanId,
            YearMonth statementMonth) {

        LoanOverviewResponse loan = ownedLoan(
                customerUserId,
                loanId
        );

        LocalDate statementFrom = statementMonth.atDay(1);
        LocalDate statementTo = statementMonth.atEndOfMonth();

        LocalDateTime from = statementFrom.atStartOfDay();
        LocalDateTime to = statementMonth.plusMonths(1)
                .atDay(1)
                .atStartOfDay();

        List<LoanMonthlyStatementEntryResponse> entries =
                repayments.findMonthlyEntries(
                        loanId,
                        from,
                        to
                );

        return new LoanMonthlyStatementResponse(
                loan.loanId(),
                loan.loanAccountNumber(),
                loan.loanType(),
                loan.status(),
                statementFrom,
                statementTo,
                loan.loanAmount(),
                loan.outstandingAmount(),
                loan.interestRate(),
                loan.emiAmount(),
                loan.nextDueDate(),
                total(entries, "EMI"),
                total(entries, "PART_PAYMENT"),
                total(entries, "FORECLOSURE"),
                entries
        );
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(
            Long customerUserId,
            Long loanId,
            YearMonth statementMonth) {

        return pdfService.create(getStatement(
                customerUserId,
                loanId,
                statementMonth
        ));
    }

    @Transactional(readOnly = true)
    public void emailPdf(
            Long customerUserId,
            String customerEmail,
            Long loanId,
            YearMonth statementMonth) {

        LoanMonthlyStatementResponse statement = getStatement(
                customerUserId,
                loanId,
                statementMonth
        );

        if (!emailService.send(customerEmail, statement)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Loan statement email could not be sent. "
                            + "Please try again later."
            );
        }
    }

    private LoanOverviewResponse ownedLoan(
            Long customerUserId,
            Long loanId) {

        return loans.findByIdAndUserId(
                loanId,
                customerUserId
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Loan was not found for this customer."
        ));
    }

    private BigDecimal total(
            List<LoanMonthlyStatementEntryResponse> entries,
            String repaymentType) {

        return entries.stream()
                .filter(entry -> repaymentType.equals(
                        entry.repaymentType()
                ))
                .map(LoanMonthlyStatementEntryResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}