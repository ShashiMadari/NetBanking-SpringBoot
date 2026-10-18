package com.example.inetbank.forex.rates;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForexRateService {

    private final ForexRateRepository rateRepository;

    public ForexRateService(
            ForexRateRepository rateRepository) {

        this.rateRepository = rateRepository;
    }

    @Transactional(readOnly = true)
    public List<ForexRateResponse> getActiveRates() {
        return rateRepository.findActiveRates();
    }
}