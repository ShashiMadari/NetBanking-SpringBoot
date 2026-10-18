package com.example.inetbank.credit.cardmanagement;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreditCardActivityService {

    private final CreditCardActivityRepository activities;
    private final CreditCardRewardRepository rewards;

    public CreditCardActivityService(
            CreditCardActivityRepository activities,
            CreditCardRewardRepository rewards) {

        this.activities = activities;
        this.rewards = rewards;
    }

    @Transactional(readOnly = true)
    public CreditCardActivityPageResponse activity(
            Long userId,
            Long cardId,
            int page,
            int size) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater."
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100."
            );
        }

        CreditCardRewardRepository.RewardCard card =
                rewards.findCustomerCreditCard(userId, cardId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Credit card was not found."
                                )
                        );

        long totalActivities = activities.countActivities(
                card.cardId()
        );

        int totalPages = totalActivities == 0
                ? 0
                : (int) Math.ceil(
                        (double) totalActivities / size
                );

        return new CreditCardActivityPageResponse(
                card.cardId(),
                card.lastFour(),
                page,
                size,
                totalActivities,
                totalPages,
                activities.findPage(
                        card.cardId(),
                        page,
                        size
                )
        );
    }
}