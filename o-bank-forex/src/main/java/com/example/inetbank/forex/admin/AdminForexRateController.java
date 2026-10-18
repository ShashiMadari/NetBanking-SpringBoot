package com.example.inetbank.forex.admin;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/forex/rates")
@PreAuthorize("hasRole('ADMIN')")
public class AdminForexRateController {

    private final AdminForexRateService rateService;

    public AdminForexRateController(
            AdminForexRateService rateService) {

        this.rateService = rateService;
    }

    @GetMapping
    public List<AdminForexRateResponse> list() {
        return rateService.list();
    }

    @PutMapping("/{currencyCode}")
    public ResponseEntity<AdminForexRateResponse> update(
            @PathVariable String currencyCode,
            @Valid @RequestBody AdminForexRateRequest request) {

        return ResponseEntity.ok(
                rateService.update(currencyCode, request)
        );
    }
}