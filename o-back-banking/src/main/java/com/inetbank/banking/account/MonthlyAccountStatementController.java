package com.inetbank.banking.account;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/statements")
@PreAuthorize("hasRole('CUSTOMER')")
public class MonthlyAccountStatementController {

    private final MonthlyAccountStatementService statementService;

    public MonthlyAccountStatementController(
            MonthlyAccountStatementService statementService) {

        this.statementService = statementService;
    }

    @GetMapping("/monthly/data")
    public MonthlyAccountStatementData data(
            @RequestParam Long accountId,
            @RequestParam int year,
            @RequestParam int month,
            Authentication authentication) {

        return statementService.getStatement(
                userId(authentication),
                accountId,
                statementMonth(year, month)
        );
    }

    @GetMapping("/monthly")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam Long accountId,
            @RequestParam int year,
            @RequestParam int month,
            Authentication authentication) {

        YearMonth statementMonth = statementMonth(year, month);

        byte[] pdf = statementService.downloadPdf(
                userId(authentication),
                accountId,
                statementMonth
        );

        String fileName = "O-Bank-Account-Statement-"
                + accountId
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

    @PostMapping("/monthly/email")
    public ResponseEntity<Map<String, String>> emailPdf(
            @RequestParam Long accountId,
            @RequestParam int year,
            @RequestParam int month,
            Authentication authentication) {

        statementService.emailPdf(
                userId(authentication),
                accountId,
                statementMonth(year, month)
        );

        return ResponseEntity.accepted().body(Map.of(
                "message",
                "Monthly account statement PDF was sent "
                        + "to your registered email."
        ));
    }

    private Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
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