package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreditCardRewardRepository {

    private final JdbcTemplate jdbcTemplate;

    public CreditCardRewardRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RewardMerchantResponse> findActiveMerchants() {

        return jdbcTemplate.query("""
                SELECT merchant_id,
                       merchant_code,
                       merchant_name,
                       merchant_category,
                       reward_eligible,
                       status
                  FROM merchants
                 WHERE status = 'ACTIVE'
                 ORDER BY merchant_name
                """,
                (row, index) -> new RewardMerchantResponse(
                        row.getLong("merchant_id"),
                        row.getString("merchant_code"),
                        row.getString("merchant_name"),
                        row.getString("merchant_category"),
                        "Y".equals(row.getString("reward_eligible")),
                        row.getString("status")
                )
        );
    }

    public Optional<RewardCard> findCustomerCreditCard(
            Long userId,
            Long cardId) {

        return jdbcTemplate.query("""
                SELECT card_id,
                       last_four
                  FROM cards
                 WHERE card_id = ?
                   AND user_id = ?
                   AND card_type = 'CREDIT'
                """,
                (row, index) -> new RewardCard(
                        row.getLong("card_id"),
                        row.getString("last_four")
                ),
                cardId,
                userId
        ).stream().findFirst();
    }

    public long availablePoints(
            Long userId,
            Long cardId) {

        Long points = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(points), 0)
                  FROM credit_card_reward_point_ledger
                 WHERE user_id = ?
                   AND card_id = ?
                """,
                Long.class,
                userId,
                cardId
        );

        return points == null ? 0L : points;
    }

    public long countLedgerEntries(
            Long userId,
            Long cardId) {

        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM credit_card_reward_point_ledger
                WHERE user_id = ?
                AND card_id = ?
                """,
                Long.class,
                userId,
                cardId
        );

        return count == null ? 0L : count;
    }

    public List<CreditCardRewardLedgerResponse> findLedgerPage(
            Long userId,
            Long cardId,
            int page,
            int size) {

        return jdbcTemplate.query("""
                SELECT reward_ledger_id,
                    merchant_purchase_id,
                    event_type,
                    points,
                    reference_number,
                    description,
                    created_at
                FROM credit_card_reward_point_ledger
                WHERE user_id = ?
                AND card_id = ?
                ORDER BY created_at DESC,
                        reward_ledger_id DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """,
                (row, index) -> new CreditCardRewardLedgerResponse(
                        row.getLong("reward_ledger_id"),
                        row.getLong("merchant_purchase_id"),
                        row.getString("event_type"),
                        row.getLong("points"),
                        row.getString("reference_number"),
                        row.getString("description"),
                        row.getTimestamp("created_at")
                                .toLocalDateTime()
                ),
                userId,
                cardId,
                page * size,
                size
        );
    }

    public Optional<RewardPurchaseCard> findPurchasableCard(
            Long userId,
            Long cardId) {

        return jdbcTemplate.query("""
                SELECT card_id,
                    last_four,
                    pin_hash,
                    daily_limit,
                    credit_limit,
                    outstanding_amount,
                    minimum_due_amount
                FROM cards
                WHERE card_id = ?
                AND user_id = ?
                AND card_type = 'CREDIT'
                AND status = 'ACTIVE'
                """,
                (row, index) -> new RewardPurchaseCard(
                        row.getLong("card_id"),
                        row.getString("last_four"),
                        row.getString("pin_hash"),
                        row.getBigDecimal("daily_limit"),
                        row.getBigDecimal("credit_limit"),
                        row.getBigDecimal("outstanding_amount"),
                        row.getBigDecimal("minimum_due_amount")
                ),
                cardId,
                userId
        ).stream().findFirst();
    }

    public Optional<RewardPurchaseMerchant> findActiveMerchant(
            Long merchantId) {

        return jdbcTemplate.query("""
                SELECT merchant_id,
                    merchant_name,
                    reward_eligible
                FROM merchants
                WHERE merchant_id = ?
                AND status = 'ACTIVE'
                """,
                (row, index) -> new RewardPurchaseMerchant(
                        row.getLong("merchant_id"),
                        row.getString("merchant_name"),
                        "Y".equals(row.getString("reward_eligible"))
                ),
                merchantId
        ).stream().findFirst();
    }

    public BigDecimal todayCardSpend(Long cardId) {

        BigDecimal total = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(card_charge_amount), 0)
                FROM credit_card_merchant_purchases
                WHERE card_id = ?
                AND status = 'SUCCESS'
                AND TRUNC(purchased_at) = TRUNC(SYSDATE)
                """,
                BigDecimal.class,
                cardId
        );

        return total == null ? BigDecimal.ZERO : total;
    }

    public boolean increaseOutstandingBalance(
            Long cardId,
            Long userId,
            BigDecimal cardChargeAmount) {

        return jdbcTemplate.update("""
                UPDATE cards
                SET outstanding_amount = outstanding_amount + ?,
                    minimum_due_amount = GREATEST(
                        minimum_due_amount,
                        ROUND(
                            (outstanding_amount + ?) * 0.05,
                            2
                        )
                    )
                WHERE card_id = ?
                AND user_id = ?
                AND card_type = 'CREDIT'
                AND status = 'ACTIVE'
                AND credit_limit - outstanding_amount >= ?
                """,
                cardChargeAmount,
                cardChargeAmount,
                cardId,
                userId,
                cardChargeAmount
        ) == 1;
    }

    public Long createMerchantPurchase(
            Long userId,
            Long cardId,
            Long merchantId,
            BigDecimal purchaseAmount,
            long pointsRedeemed,
            BigDecimal pointsDiscountAmount,
            BigDecimal cardChargeAmount,
            long pointsEarned,
            String referenceNumber) {

        jdbcTemplate.update("""
                INSERT INTO credit_card_merchant_purchases (
                    user_id,
                    card_id,
                    merchant_id,
                    purchase_amount,
                    points_redeemed,
                    points_discount_amount,
                    card_charge_amount,
                    points_earned,
                    reference_number,
                    status,
                    purchased_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', SYSTIMESTAMP)
                """,
                userId,
                cardId,
                merchantId,
                purchaseAmount,
                pointsRedeemed,
                pointsDiscountAmount,
                cardChargeAmount,
                pointsEarned,
                referenceNumber
        );

        return jdbcTemplate.queryForObject("""
                SELECT merchant_purchase_id
                FROM credit_card_merchant_purchases
                WHERE reference_number = ?
                """,
                Long.class,
                referenceNumber
        );
    }

    public void createLedgerEvent(
            Long userId,
            Long cardId,
            Long merchantPurchaseId,
            String eventType,
            long points,
            String referenceNumber,
            String description) {

        jdbcTemplate.update("""
                INSERT INTO credit_card_reward_point_ledger (
                    user_id,
                    card_id,
                    merchant_purchase_id,
                    event_type,
                    points,
                    reference_number,
                    description,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP)
                """,
                userId,
                cardId,
                merchantPurchaseId,
                eventType,
                points,
                referenceNumber,
                description
        );
    }

    public record RewardCard(
            Long cardId,
            String lastFour) {
    }

    public record RewardPurchaseCard(
            Long cardId,
            String lastFour,
            String pinHash,
            BigDecimal dailyLimit,
            BigDecimal creditLimit,
            BigDecimal outstandingAmount,
            BigDecimal minimumDueAmount) {
    }

    public record RewardPurchaseMerchant(
            Long merchantId,
            String merchantName,
            boolean rewardEligible) {
    }

    public long countMerchantPurchases(
            Long userId,
            Long cardId) {

        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM credit_card_merchant_purchases
                WHERE user_id = ?
                AND card_id = ?
                """,
                Long.class,
                userId,
                cardId
        );

        return count == null ? 0L : count;
    }

    public List<MerchantPurchaseHistoryResponse> findMerchantPurchases(
            Long userId,
            Long cardId,
            int page,
            int size) {

        return jdbcTemplate.query("""
                SELECT p.merchant_purchase_id,
                    p.card_id,
                    c.last_four,
                    p.merchant_id,
                    m.merchant_name,
                    m.merchant_category,
                    p.purchase_amount,
                    p.points_redeemed,
                    p.points_discount_amount,
                    p.card_charge_amount,
                    p.points_earned,
                    p.reference_number,
                    p.status,
                    p.purchased_at,
                    p.reversal_reference_number,
                    p.reversal_reason,
                    p.reversed_at
                FROM credit_card_merchant_purchases p
                JOIN cards c
                    ON c.card_id = p.card_id
                JOIN merchants m
                    ON m.merchant_id = p.merchant_id
                WHERE p.user_id = ?
                AND p.card_id = ?
                ORDER BY p.merchant_purchase_id DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """,
                (row, index) -> new MerchantPurchaseHistoryResponse(
                        row.getLong("merchant_purchase_id"),
                        row.getLong("card_id"),
                        row.getString("last_four"),
                        row.getLong("merchant_id"),
                        row.getString("merchant_name"),
                        row.getString("merchant_category"),
                        row.getBigDecimal("purchase_amount"),
                        row.getLong("points_redeemed"),
                        row.getBigDecimal("points_discount_amount"),
                        row.getBigDecimal("card_charge_amount"),
                        row.getLong("points_earned"),
                        row.getString("reference_number"),
                        row.getString("status"),
                        row.getTimestamp("purchased_at")
                                .toLocalDateTime(),
                        row.getString("reversal_reference_number"),
                        row.getString("reversal_reason"),
                        row.getTimestamp("reversed_at") == null
                                ? null
                                : row.getTimestamp("reversed_at")
                                        .toLocalDateTime()
                ),
                userId,
                cardId,
                page * size,
                size
        );
    }
}