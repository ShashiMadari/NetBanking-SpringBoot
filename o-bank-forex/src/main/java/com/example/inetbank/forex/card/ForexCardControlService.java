package com.example.inetbank.forex.card;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ForexCardControlService {

    private final ForexCardControlRepository repository;

    public ForexCardControlService(
            ForexCardControlRepository repository) {

        this.repository = repository;
    }

    @Transactional
    public ForexCardResponse changeStatus(
            Long userId,
            Long cardId,
            ForexCardStatusRequest request) {

        ForexCardResponse card =
                cardOrThrow(userId, cardId);

        String status = request.status()
                .trim()
                .toUpperCase(Locale.ROOT);

        if ("CLOSED".equals(card.status())
                && "ACTIVE".equals(status)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A closed Forex card cannot be reactivated."
            );
        }

        repository.updateStatus(cardId, userId, status);

        return cardOrThrow(userId, cardId);
    }

    @Transactional
    public ForexCardResponse changeSpendLimit(
            Long userId,
            Long cardId,
            ForexCardAmountRequest request) {

        cardOrThrow(userId, cardId);

        repository.updateSpendLimit(
                cardId,
                userId,
                request.amount()
        );

        return cardOrThrow(userId, cardId);
    }

    private ForexCardResponse cardOrThrow(
            Long userId,
            Long cardId) {

        ForexCardResponse card =
                repository.findByIdAndUserId(cardId, userId);

        if (card == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Forex card was not found."
            );
        }

        return card;
    }
}