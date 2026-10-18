package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.inetbank.credit.integration.IdentityNotificationClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;

@Service
public class CreditCardRewardService {

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private final CreditCardRewardRepository rewards;
    private final PasswordEncoder passwordEncoder;
    private final MerchantPurchaseEmailService emailService;
    private final IdentityNotificationClient notifications;
    private final IdentitySystemNotificationClient audits;

    public CreditCardRewardService(
            CreditCardRewardRepository rewards,
            PasswordEncoder passwordEncoder,
            MerchantPurchaseEmailService emailService,
            IdentityNotificationClient notifications,
            IdentitySystemNotificationClient audits) {

        this.rewards = rewards;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.notifications = notifications;
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public List<RewardMerchantResponse> merchants() {
        return rewards.findActiveMerchants();
    }

    @Transactional(readOnly = true)
    public CreditCardRewardBalanceResponse balance(
            Long userId,
            Long cardId) {

        CreditCardRewardRepository.RewardCard card =
                rewards.findCustomerCreditCard(userId, cardId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Credit card was not found."
                                )
                        );

        long points = rewards.availablePoints(
                userId,
                card.cardId()
        );

        return new CreditCardRewardBalanceResponse(
                card.cardId(),
                card.lastFour(),
                points,
                "1 point = INR 1; earn 1 point per eligible INR 100."
        );
    }

    @Transactional(readOnly = true)
    public CreditCardRewardLedgerPageResponse ledger(
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

        long totalEntries = rewards.countLedgerEntries(
                userId,
                card.cardId()
        );

        int totalPages = totalEntries == 0
                ? 0
                : (int) Math.ceil(
                        (double) totalEntries / size
                );

        return new CreditCardRewardLedgerPageResponse(
                card.cardId(),
                page,
                size,
                totalEntries,
                totalPages,
                rewards.availablePoints(userId, card.cardId()),
                rewards.findLedgerPage(
                        userId,
                        card.cardId(),
                        page,
                        size
                )
        );
    }

    @Transactional
    public MerchantPurchaseResponse purchase(
            Long userId,
            String customerEmail,
            String bearerToken,
            MerchantPurchaseRequest request) {

        CreditCardRewardRepository.RewardPurchaseCard card =
                purchasableCard(userId, request.cardId());

        if (card.pinHash() == null
                || !passwordEncoder.matches(
                        request.cardPin(),
                        card.pinHash())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid card PIN."
            );
        }

        CreditCardRewardRepository.RewardPurchaseMerchant merchant =
                rewards.findActiveMerchant(request.merchantId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Selected merchant is not active."
                                )
                        );

        if (!merchant.rewardEligible()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selected merchant is not eligible for reward purchases."
            );
        }

        long availablePoints = rewards.availablePoints(
                userId,
                card.cardId()
        );

        long pointsToRedeem = request.pointsToRedeem();

        if (pointsToRedeem > availablePoints) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient reward points."
            );
        }

        BigDecimal pointsDiscount =
                BigDecimal.valueOf(pointsToRedeem);

        if (pointsDiscount.compareTo(request.purchaseAmount()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Redeemed points cannot exceed the purchase amount."
            );
        }

        BigDecimal cardChargeAmount =
                request.purchaseAmount().subtract(pointsDiscount);

        ensureDailyLimit(
                card,
                cardChargeAmount
        );

        ensureAvailableCredit(
                card,
                cardChargeAmount
        );

        long pointsEarned = cardChargeAmount
                .divide(ONE_HUNDRED, 0, RoundingMode.DOWN)
                .longValue();

        String referenceNumber = referenceNumber();

        if (!rewards.increaseOutstandingBalance(
                card.cardId(),
                userId,
                cardChargeAmount)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Available credit changed. Please try again."
            );
        }

        Long merchantPurchaseId = rewards.createMerchantPurchase(
                userId,
                card.cardId(),
                merchant.merchantId(),
                request.purchaseAmount(),
                pointsToRedeem,
                pointsDiscount,
                cardChargeAmount,
                pointsEarned,
                referenceNumber
        );

        if (pointsToRedeem > 0) {
            rewards.createLedgerEvent(
                    userId,
                    card.cardId(),
                    merchantPurchaseId,
                    "REDEEM",
                    -pointsToRedeem,
                    referenceNumber + "-REDEEM",
                    "Reward points redeemed at merchant purchase."
            );
        }

        if (pointsEarned > 0) {
            rewards.createLedgerEvent(
                    userId,
                    card.cardId(),
                    merchantPurchaseId,
                    "EARN",
                    pointsEarned,
                    referenceNumber + "-EARN",
                    "Reward points earned from merchant purchase."
            );
        }

        emailService.sendSuccess(
                customerEmail,
                card.lastFour(),
                merchant.merchantName(),
                request.purchaseAmount(),
                cardChargeAmount,
                pointsToRedeem,
                pointsEarned,
                referenceNumber
        );

        notifications.createNotification(
                bearerToken,
                "PAYMENT",
                "Credit-card purchase of INR "
                        + request.purchaseAmount().toPlainString()
                        + " at "
                        + merchant.merchantName()
                        + " was completed successfully. Reference: "
                        + referenceNumber
        );

        audits.createAuditEvent(
                userId,
                "CREDIT_CARD_MERCHANT_PURCHASE",
                "CREDIT_CARD_MERCHANT_PURCHASE",
                String.valueOf(merchantPurchaseId)
        );

        return new MerchantPurchaseResponse(
                merchantPurchaseId,
                referenceNumber,
                request.purchaseAmount(),
                pointsToRedeem,
                pointsDiscount,
                cardChargeAmount,
                pointsEarned,
                "SUCCESS",
                java.time.LocalDateTime.now(),
                "Merchant purchase completed successfully."
        );
    }

    private CreditCardRewardRepository.RewardPurchaseCard purchasableCard(
            Long userId,
            Long cardId) {

        return rewards.findPurchasableCard(userId, cardId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Active credit card was not found."
                        )
                );
    }

    private void ensureDailyLimit(
            CreditCardRewardRepository.RewardPurchaseCard card,
            BigDecimal cardChargeAmount) {

        BigDecimal todaySpend = rewards.todayCardSpend(
                card.cardId()
        );

        if (todaySpend.add(cardChargeAmount)
                .compareTo(card.dailyLimit()) > 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Purchase exceeds the card daily limit."
            );
        }
    }

    private void ensureAvailableCredit(
            CreditCardRewardRepository.RewardPurchaseCard card,
            BigDecimal cardChargeAmount) {

        BigDecimal availableCredit = card.creditLimit()
                .subtract(card.outstandingAmount());

        if (cardChargeAmount.compareTo(availableCredit) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Purchase exceeds available credit."
            );
        }
    }

    private String referenceNumber() {
        return "MRC-"
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 16)
                        .toUpperCase();
    }

    @Transactional(readOnly = true)
    public MerchantPurchaseHistoryPageResponse purchases(
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

        long totalPurchases = rewards.countMerchantPurchases(
                userId,
                card.cardId()
        );

        int totalPages = totalPurchases == 0
                ? 0
                : (int) Math.ceil(
                        (double) totalPurchases / size
                );

        return new MerchantPurchaseHistoryPageResponse(
                card.cardId(),
                page,
                size,
                totalPurchases,
                totalPages,
                rewards.findMerchantPurchases(
                        userId,
                        card.cardId(),
                        page,
                        size
                )
        );
    }
}