package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminMerchantPurchaseRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminMerchantPurchaseRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public long count(
            String status,
            Long merchantId) {

        QueryParts query = filters(status, merchantId);

        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM credit_card_merchant_purchases p
                """
                        + query.whereClause(),
                Long.class,
                query.parameters().toArray()
        );
    }

    public List<AdminMerchantPurchaseResponse> findPage(
            String status,
            Long merchantId,
            int page,
            int size) {

        QueryParts query = filters(status, merchantId);

        List<Object> parameters = new ArrayList<>(
                query.parameters()
        );

        parameters.add(page * size);
        parameters.add(size);

        return jdbcTemplate.query("""
                SELECT p.merchant_purchase_id,
                       p.reference_number,
                       p.status,

                       p.user_id,

                       p.card_id,
                       c.last_four,

                       p.merchant_id,
                       m.merchant_code,
                       m.merchant_name,
                       m.merchant_category,

                       p.purchase_amount,
                       p.points_redeemed,
                       p.points_discount_amount,
                       p.card_charge_amount,
                       p.points_earned,

                       p.purchased_at,

                       p.reversal_reference_number,
                       p.reversal_reason,
                       p.reversed_at
                  FROM credit_card_merchant_purchases p
                  JOIN cards c
                    ON c.card_id = p.card_id
                  JOIN merchants m
                    ON m.merchant_id = p.merchant_id
                """
                        + query.whereClause()
                        + """
                           ORDER BY p.purchased_at DESC,
                                    p.merchant_purchase_id DESC
                           OFFSET ? ROWS
                           FETCH NEXT ? ROWS ONLY
                          """,
                (row, index) -> map(row),
                parameters.toArray()
        );
    }

    public Optional<AdminMerchantPurchaseResponse> findById(
            Long merchantPurchaseId) {

        return jdbcTemplate.query("""
                SELECT p.merchant_purchase_id,
                       p.reference_number,
                       p.status,

                       p.user_id,

                       p.card_id,
                       c.last_four,

                       p.merchant_id,
                       m.merchant_code,
                       m.merchant_name,
                       m.merchant_category,

                       p.purchase_amount,
                       p.points_redeemed,
                       p.points_discount_amount,
                       p.card_charge_amount,
                       p.points_earned,

                       p.purchased_at,

                       p.reversal_reference_number,
                       p.reversal_reason,
                       p.reversed_at
                  FROM credit_card_merchant_purchases p
                  JOIN cards c
                    ON c.card_id = p.card_id
                  JOIN merchants m
                    ON m.merchant_id = p.merchant_id
                 WHERE p.merchant_purchase_id = ?
                """,
                (row, index) -> map(row),
                merchantPurchaseId
        ).stream().findFirst();
    }

    private QueryParts filters(
            String status,
            Long merchantId) {

        StringBuilder where = new StringBuilder(
                " WHERE 1 = 1 "
        );

        List<Object> parameters = new ArrayList<>();

        if (!"ALL".equals(status)) {
            where.append(" AND p.status = ? ");
            parameters.add(status);
        }

        if (merchantId != null) {
            where.append(" AND p.merchant_id = ? ");
            parameters.add(merchantId);
        }

        return new QueryParts(
                where.toString(),
                parameters
        );
    }

    private AdminMerchantPurchaseResponse map(
            java.sql.ResultSet row) throws java.sql.SQLException {

        return new AdminMerchantPurchaseResponse(
                row.getLong("merchant_purchase_id"),
                row.getString("reference_number"),
                row.getString("status"),

                row.getLong("user_id"),
                null,
                null,

                row.getLong("card_id"),
                row.getString("last_four"),

                row.getLong("merchant_id"),
                row.getString("merchant_code"),
                row.getString("merchant_name"),
                row.getString("merchant_category"),

                row.getBigDecimal("purchase_amount"),
                row.getLong("points_redeemed"),
                row.getBigDecimal("points_discount_amount"),
                row.getBigDecimal("card_charge_amount"),
                row.getLong("points_earned"),

                row.getObject(
                        "purchased_at",
                        LocalDateTime.class
                ),

                row.getString("reversal_reference_number"),
                row.getString("reversal_reason"),
                row.getObject(
                        "reversed_at",
                        LocalDateTime.class
                )
        );
    }

    private record QueryParts(
            String whereClause,
            List<Object> parameters
    ) {
    }
}