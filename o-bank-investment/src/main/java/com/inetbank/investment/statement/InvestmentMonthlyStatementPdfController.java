package com.inetbank.investment.statement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.inetbank.investment.InvestmentService;

@RestController
@RequestMapping("/api/investments/statements")
@PreAuthorize("hasRole('CUSTOMER')")
public class InvestmentMonthlyStatementPdfController {

    private final InvestmentService investmentService;
    private final InvestmentMonthlyStatementPdfService pdfService;
    private final InvestmentMonthlyStatementEmailService emailService;

    public InvestmentMonthlyStatementPdfController(
            InvestmentService investmentService,
            InvestmentMonthlyStatementPdfService pdfService,
            InvestmentMonthlyStatementEmailService emailService) {

        this.investmentService = investmentService;
        this.pdfService = pdfService;
        this.emailService = emailService;
    }

    @GetMapping(
            value = "/monthly/pdf",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> download(
            Authentication authentication,
            @RequestParam int year,
            @RequestParam int month) {

        Map<String, Object> statement =
                investmentService.monthlyStatement(
                        authentication.getName(),
                        year,
                        month
                );

        String filename = "O-Bank-Investment-Statement-"
                + statement.get("statementMonth")
                + ".pdf";

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfService.create(statement));
    }

    @PostMapping("/monthly/email")
    public Map<String, Object> email(
            Authentication authentication,
            @RequestParam int year,
            @RequestParam int month) {

        String customerIdentity = authentication.getName();

        Map<String, Object> statement =
                investmentService.monthlyStatement(
                        customerIdentity,
                        year,
                        month
                );

        String customerEmail =
                investmentService.getCustomerEmail(
                        customerIdentity
                );

        emailService.send(customerEmail, statement);

        // NEW: notify only after the statement email was sent.
        investmentService.notifyStatementEmailSent(
                customerIdentity,
                String.valueOf(statement.get("statementMonth"))
        );

        Object entries = statement.get("entries");

        int transactionCount = entries instanceof List<?> list
                ? list.size()
                : 0;

        Map<String, Object> response = new LinkedHashMap<>();

        response.put(
                "message",
                "Your Investment statement PDF was sent to your registered email."
        );
        response.put("statementMonth", statement.get("statementMonth"));
        response.put("statementFrom", statement.get("statementFrom"));
        response.put("statementTo", statement.get("statementTo"));
        response.put("transactionCount", transactionCount);

        return response;
    }
}