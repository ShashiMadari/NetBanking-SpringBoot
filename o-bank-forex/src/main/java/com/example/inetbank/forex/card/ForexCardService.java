package com.example.inetbank.forex.card;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ForexCardService {

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private final ForexCardRepository repository;

    public ForexCardService(
            ForexCardRepository repository) {

        this.repository = repository;
    }

    @Transactional
    public ForexCardResponse issue(
            Long userId,
            ForexCardIssueRequest request) {

        if (!repository.isKycApproved(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Forex KYC must be approved before issuing a Forex card."
            );
        }

        String currencyCode = request.currencyCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        if ("INR".equals(currencyCode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A Forex card must use a foreign-currency wallet."
            );
        }

        if (!repository.hasActiveRate(currencyCode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The selected Forex card currency is unavailable."
            );
        }

        Long walletId = repository.findWalletId(
                userId,
                currencyCode
        );

        if (walletId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Create a " + currencyCode
                            + " wallet before issuing this Forex card."
            );
        }

        String cardToken = "FXC-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(Locale.ROOT);

        String lastFour = String.format(
                "%04d",
                RANDOM.nextInt(10_000)
        );

        Long cardId = repository.create(
                userId,
                walletId,
                "FOREX **** " + lastFour,
                cardToken,
                request.spendLimit()
        );

        return repository.findByIdAndUserId(
                cardId,
                userId
        );
    }

    @Transactional(readOnly = true)
    public List<ForexCardResponse> list(Long userId) {
        return repository.findByUserId(userId);
    }
}