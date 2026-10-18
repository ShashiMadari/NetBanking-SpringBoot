package com.example.inetbank.credit.loan;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/loans")
@PreAuthorize("hasRole('CUSTOMER')")
public class LoanMonthlyStatementController {

    private final LoanMonthlyStatementService statementService;

    public LoanMonthlyStatementController(
            LoanMonthlyStatementService statementService) {

        this.statementService = statementService;
    }

    @GetMapping("/{loanId}/monthly-statement")
    public LoanMonthlyStatementResponse statement(
            @PathVariable Long loanId,
            @RequestParam int year,
            @RequestParam int month,
            Authentication authentication) {

        return statementService.getStatement(
                userId(authentication),
                loanId,
                statementMonth(year, month)
        );
    }

    @GetMapping("/{loanId}/monthly-statement/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable Long loanId,
            @RequestParam int year,
            @RequestParam int month,
            Authentication authentication) {

        YearMonth statementMonth = statementMonth(year, month);

        byte[] pdf = statementService.downloadPdf(
                userId(authentication),
                loanId,
                statementMonth
        );

        String fileName = "O-Bank-Loan-Statement-"
                + loanId
                + "-"
                + statementMonth
                + ".pdf";

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\""
                )
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/{loanId}/monthly-statement/email")
    public ResponseEntity<Map<String, String>> emailPdf(
            @PathVariable Long loanId,
            @RequestParam int year,
            @RequestParam int month,
            Authentication authentication) {

        statementService.emailPdf(
                userId(authentication),
                customerEmail(authentication),
                loanId,
                statementMonth(year, month)
        );

        return ResponseEntity.accepted().body(Map.of(
                "message",
                "Monthly loan statement PDF was sent "
                        + "to your registered email."
        ));
    }

    private Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    private String customerEmail(Authentication authentication) {
        Object details = authentication.getDetails();

        if (details instanceof String email && !email.isBlank()) {
            return email;
        }

        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authenticated customer email was not found."
        );
    }

    private YearMonth statementMonth(int year, int month) {
        try {
            return YearMonth.of(year, month);
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Year and month are invalid."
            );
        }
    }
}