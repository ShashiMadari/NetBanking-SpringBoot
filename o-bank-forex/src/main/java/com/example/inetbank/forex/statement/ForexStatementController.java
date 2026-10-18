package com.example.inetbank.forex.statement;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/forex/transactions")
@PreAuthorize("hasRole('CUSTOMER')")
public class ForexStatementController {

    private final ForexStatementService statementService;
    private final ForexStatementPdfService pdfService;
    private final ForexStatementDeliveryService deliveryService;

    public ForexStatementController(
            ForexStatementService statementService,
            ForexStatementPdfService pdfService,
            ForexStatementDeliveryService deliveryService) {

        this.statementService = statementService;
        this.pdfService = pdfService;
        this.deliveryService = deliveryService;
    }

    @GetMapping("/statement")
    public ForexTransactionStatementResponse getStatement(
            @RequestParam(defaultValue = "RECENT")
            ForexStatementPeriod period,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int pageSize,

            Authentication authentication) {

        return statementService.getStatement(
                Long.valueOf(authentication.getName()),
                period,
                from,
                to,
                page,
                pageSize
        );
    }

    @GetMapping(
            value = "/statement/pdf",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam(defaultValue = "RECENT")
            ForexStatementPeriod period,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "100")
            int pageSize,

            Authentication authentication) {

        ForexTransactionStatementResponse statement =
                statementService.getStatement(
                        Long.valueOf(authentication.getName()),
                        period,
                        from,
                        to,
                        page,
                        pageSize
                );

        String filename = "O-Bank-Forex-Statement-"
                + statement.statementFrom()
                + "-to-"
                + statement.statementTo()
                + ".pdf";

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfService.create(statement));
    }

    @PostMapping("/statement/email")
    public ForexStatementEmailResponse emailStatement(
            @Valid @RequestBody ForexStatementEmailRequest request,
            Authentication authentication) {

        return deliveryService.send(
                Long.valueOf(authentication.getName()),
                request
        );
    }
}