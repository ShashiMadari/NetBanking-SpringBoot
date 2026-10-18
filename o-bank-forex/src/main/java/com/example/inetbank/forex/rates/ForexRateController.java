package com.example.inetbank.forex.rates;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forex")
@PreAuthorize("hasRole('CUSTOMER')")
public class ForexRateController {

    private final ForexRateService rateService;

    public ForexRateController(
            ForexRateService rateService) {

        this.rateService = rateService;
    }

    @GetMapping("/rates")
    public List<ForexRateResponse> getRates() {
        return rateService.getActiveRates();
    }
}