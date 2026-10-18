package com.example.inetbank.forex.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ForexQuoteService {

    private static final BigDecimal BPS_DIVISOR =
            BigDecimal.valueOf(10_000);

    private final ForexQuoteRepository quoteRepository;
    private final int quoteValiditySeconds;

    public ForexQuoteService(
            ForexQuoteRepository quoteRepository,
            @Value("${app.forex.quote-validity-seconds:60}")
            int quoteValiditySeconds) {

        this.quoteRepository = quoteRepository;
        this.quoteValiditySeconds = quoteValiditySeconds;
    }

    @Transactional
    public ForexQuoteResponse createQuote(
            Long userId,
            ForexQuoteRequest request) {

        if (!quoteRepository.isKycApproved(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Forex KYC must be approved before creating a quote."
            );
        }

        String fromCurrency = currency(request.fromCurrency());
        String toCurrency = currency(request.toCurrency());

        validateInrPair(fromCurrency, toCurrency);

        String foreignCurrency = "INR".equals(fromCurrency)
                ? toCurrency
                : fromCurrency;

        ForexQuoteRepository.ActiveRate rate =
                quoteRepository.findActiveRate(foreignCurrency)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "The selected currency is unavailable."
                                )
                        );

        BigDecimal markup = BigDecimal.valueOf(rate.markupBps())
                .divide(BPS_DIVISOR, 8, RoundingMode.HALF_UP);

        BigDecimal customerRateInr = "INR".equals(fromCurrency)
                ? rate.currentRateInr().multiply(
                        BigDecimal.ONE.add(markup)
                )
                : rate.currentRateInr().multiply(
                        BigDecimal.ONE.subtract(markup)
                );

        BigDecimal receiveAmount = "INR".equals(fromCurrency)
                ? request.fromAmount().divide(
                        customerRateInr,
                        6,
                        RoundingMode.DOWN
                )
                : request.fromAmount().multiply(customerRateInr)
                        .setScale(2, RoundingMode.DOWN);

        String quoteId = UUID.randomUUID().toString();

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(quoteValiditySeconds);

        quoteRepository.saveQuote(
                quoteId,
                userId,
                fromCurrency,
                toCurrency,
                request.fromAmount(),
                receiveAmount,
                customerRateInr,
                rate.markupBps(),
                expiresAt
        );

        return new ForexQuoteResponse(
                quoteId,
                fromCurrency,
                toCurrency,
                request.fromAmount(),
                receiveAmount,
                customerRateInr,
                rate.markupBps(),
                expiresAt.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                "LOCKED"
        );
    }

    private String currency(String value) {
        String currency = value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);

        if (!currency.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Currency must be a three-letter currency code."
            );
        }

        return currency;
    }

    private void validateInrPair(
            String fromCurrency,
            String toCurrency) {

        if (fromCurrency.equals(toCurrency)
                || (!"INR".equals(fromCurrency)
                && !"INR".equals(toCurrency))) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only INR-to-foreign and foreign-to-INR conversions are supported."
            );
        }
    }
}
