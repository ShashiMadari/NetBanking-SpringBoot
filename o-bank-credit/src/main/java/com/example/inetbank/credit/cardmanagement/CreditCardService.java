package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.inetbank.credit.integration.IdentityNotificationClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;

@Service
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final IdentityNotificationClient identityNotificationClient;
    private final IdentitySystemNotificationClient identitySystemNotificationClient;
    private final CardControlEmailService cardControlEmailService;

    public CreditCardService(
            CreditCardRepository creditCardRepository,
            IdentityNotificationClient identityNotificationClient,
            IdentitySystemNotificationClient identitySystemNotificationClient,
            CardControlEmailService cardControlEmailService) {

        this.creditCardRepository = creditCardRepository;
        this.identityNotificationClient = identityNotificationClient;
        this.identitySystemNotificationClient = identitySystemNotificationClient;
        this.cardControlEmailService = cardControlEmailService;
    }

    @Transactional(readOnly = true)
    public List<CreditCardResponse> list(Long userId) {
        return creditCardRepository.findByUserId(userId);
    }

    @Transactional
    public CardActionResponse block(
            Long cardId,
            Long userId,
            String customerEmail,
            String bearerToken) {

        CreditCardRepository.CardControlDetails card =
                card(cardId, userId);

        if (!"ACTIVE".equals(card.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an active card can be blocked."
            );
        }

        if (!creditCardRepository.updateStatus(
                cardId, userId, "ACTIVE", "BLOCKED")) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Card status changed. Please try again."
            );
        }

        String message = "Your " + card.cardType().toLowerCase()
                + " card ending " + card.lastFour()
                + " was blocked.";

        identityNotificationClient.createNotification(
                bearerToken,
                "SECURITY",
                message
        );

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "CARD_BLOCKED",
                "CARD",
                String.valueOf(cardId)
        );

        cardControlEmailService.sendStatusChange(
                customerEmail,
                card,
                "blocked"
        );

        return new CardActionResponse(
                card.cardId(),
                card.lastFour(),
                card.cardType(),
                "BLOCKED",
                card.dailyLimit(),
                "Card blocked successfully."
        );
    }

    @Transactional
    public CardActionResponse unblock(
            Long cardId,
            Long userId,
            String customerEmail,
            String bearerToken) {

        CreditCardRepository.CardControlDetails card =
                card(cardId, userId);

        if (!"BLOCKED".equals(card.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a blocked card can be unblocked."
            );
        }

        if (!creditCardRepository.updateStatus(
                cardId, userId, "BLOCKED", "ACTIVE")) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Card status changed. Please try again."
            );
        }

        String message = "Your " + card.cardType().toLowerCase()
                + " card ending " + card.lastFour()
                + " was unblocked.";

        identityNotificationClient.createNotification(
                bearerToken,
                "SECURITY",
                message
        );

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "CARD_UNBLOCKED",
                "CARD",
                String.valueOf(cardId)
        );

        cardControlEmailService.sendStatusChange(
                customerEmail,
                card,
                "unblocked"
        );

        return new CardActionResponse(
                card.cardId(),
                card.lastFour(),
                card.cardType(),
                "ACTIVE",
                card.dailyLimit(),
                "Card unblocked successfully."
        );
    }

    @Transactional
    public CardActionResponse updateDailyLimit(
            Long cardId,
            Long userId,
            String customerEmail,
            String bearerToken,
            BigDecimal dailyLimit) {

        CreditCardRepository.CardControlDetails card =
                card(cardId, userId);

        if (!"ACTIVE".equals(card.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an active card can have its daily limit updated."
            );
        }

        if ("CREDIT".equals(card.cardType())
                && dailyLimit.compareTo(card.creditLimit()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Daily limit cannot exceed the credit limit of INR "
                            + card.creditLimit().toPlainString() + "."
            );
        }

        if (!creditCardRepository.updateDailyLimit(
                cardId, userId, dailyLimit)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Card daily limit could not be updated."
            );
        }

        String message = "The daily limit for your "
                + card.cardType().toLowerCase()
                + " card ending " + card.lastFour()
                + " was updated.";

        identityNotificationClient.createNotification(
                bearerToken,
                "SECURITY",
                message
        );

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "CARD_DAILY_LIMIT_UPDATED",
                "CARD",
                String.valueOf(cardId)
        );

        cardControlEmailService.sendDailyLimitChange(
                customerEmail,
                card,
                dailyLimit
        );

        return new CardActionResponse(
                card.cardId(),
                card.lastFour(),
                card.cardType(),
                "ACTIVE",
                dailyLimit,
                "Card daily limit updated successfully."
        );
    }

    private CreditCardRepository.CardControlDetails card(
            Long cardId,
            Long userId) {

        return creditCardRepository.findControlDetails(
                cardId,
                userId
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Card was not found for the authenticated customer."
        ));
    }
}
