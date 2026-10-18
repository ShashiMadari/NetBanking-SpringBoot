package com.example.inetbank.forex.admin;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminForexRateService {

    private final AdminForexRateRepository repository;

    public AdminForexRateService(
            AdminForexRateRepository repository) {

        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AdminForexRateResponse> list() {
        return repository.findAll();
    }

    @Transactional
    public AdminForexRateResponse update(
            String currencyCode,
            AdminForexRateRequest request) {

        String currency = currencyCode.trim()
                .toUpperCase(Locale.ROOT);

        if ("INR".equals(currency)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "INR is the base currency and cannot be edited."
            );
        }

        AdminForexRateResponse existing =
                repository.findByCurrencyCode(currency);

        if (existing == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Forex currency was not found."
            );
        }

        repository.update(
                currency,
                request.baseRateInr(),
                request.markupBps(),
                request.active()
        );

        repository.createHistory(
                currency,
                existing.baseRateInr(),
                request.baseRateInr()
        );

        return repository.findByCurrencyCode(currency);
    }
}