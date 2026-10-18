package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminMerchantPurchaseReversalRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminMerchantPurchaseReversalRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public void reverse(
            Long adminUserId,
            Long merchantPurchaseId,
            String reason) {

        jdbcTemplate.update("""
                BEGIN
                    pkg_credit_card_admin.reverse_merchant_purchase(
                        ?,
                        ?,
                        ?
                    );
                END;
                """,
                adminUserId,
                merchantPurchaseId,
                reason
        );
    }

    public Optional<ReversalDetails> findById(
            Long merchantPurchaseId) {

        return jdbcTemplate.query("""
                SELECT p.merchant_purchase_id,
                       p.user_id,
                       p.card_id,
                       c.last_four,
                       p.reference_number,
                       p.reversal_reference_number,
                       p.card_charge_amount,
                       p.points_redeemed,
                       p.points_earned,
                       p.status,
                       p.reversal_reason,
                       p.reversed_at
                  FROM credit_card_merchant_purchases p
                  JOIN cards c
                    ON c.card_id = p.card_id
                 WHERE p.merchant_purchase_id = ?
                """,
                (row, index) -> new ReversalDetails(
                        row.getLong("merchant_purchase_id"),
                        row.getLong("user_id"),
                        row.getLong("card_id"),
                        row.getString("last_four"),
                        row.getString("reference_number"),
                        row.getString("reversal_reference_number"),
                        row.getBigDecimal("card_charge_amount"),
                        row.getLong("points_redeemed"),
                        row.getLong("points_earned"),
                        row.getString("status"),
                        row.getString("reversal_reason"),
                        row.getObject(
                                "reversed_at",
                                LocalDateTime.class
                        )
                ),
                merchantPurchaseId
        ).stream().findFirst();
    }

    public record ReversalDetails(
            Long merchantPurchaseId,
            Long customerUserId,
            Long cardId,
            String cardLastFour,
            String referenceNumber,
            String reversalReferenceNumber,
            BigDecimal cardChargeAmount,
            long pointsRedeemed,
            long pointsEarned,
            String status,
            String reversalReason,
            LocalDateTime reversedAt) {
    }
}