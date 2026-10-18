package com.example.inetbank.forex.conversion;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forex")
@PreAuthorize("hasRole('CUSTOMER')")
public class ForexTransactionHistoryController {

    private final ForexTransactionHistoryService historyService;

    public ForexTransactionHistoryController(
            ForexTransactionHistoryService historyService) {

        this.historyService = historyService;
    }

    @GetMapping("/transactions")
    public List<ForexTransactionResponse> getTransactions(
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            Authentication authentication) {

        return historyService.getTransactions(
                Long.valueOf(authentication.getName()),
                currency,
                type,
                status,
                from,
                to
        );
    }
}