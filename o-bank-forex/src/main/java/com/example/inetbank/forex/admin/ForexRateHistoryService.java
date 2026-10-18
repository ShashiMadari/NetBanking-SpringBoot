package com.example.inetbank.forex.admin;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ForexRateHistoryService {

    private final ForexRateHistoryRepository repository;
    private final AdminForexRateRepository rateRepository;

    public ForexRateHistoryService(
            ForexRateHistoryRepository repository,
            AdminForexRateRepository rateRepository) {

        this.repository = repository;
        this.rateRepository = rateRepository;
    }

    @Transactional(readOnly = true)
    public List<ForexRateHistoryResponse> getHistory(
            String currencyCode) {

        String currency = currencyCode.trim()
                .toUpperCase(Locale.ROOT);

        if (rateRepository.findByCurrencyCode(currency) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Forex currency was not found."
            );
        }

        return repository.findByCurrencyCode(currency);
    }
}