package com.example.inetbank.forex.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/forex/rates")
@PreAuthorize("hasRole('ADMIN')")
public class ForexRateHistoryController {

    private final ForexRateHistoryService historyService;

    public ForexRateHistoryController(
            ForexRateHistoryService historyService) {

        this.historyService = historyService;
    }

    @GetMapping("/{currencyCode}/history")
    public List<ForexRateHistoryResponse> getHistory(
            @PathVariable String currencyCode) {

        return historyService.getHistory(currencyCode);
    }
}