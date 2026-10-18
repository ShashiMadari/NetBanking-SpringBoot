package com.inetbank.banking.adminpayment;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.inetbank.banking.integration.IdentitySystemNotificationClient;
import com.inetbank.banking.integration.IdentityNotificationClient;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;

@Service
public class PaymentReversalService {

    private final EntityManager entityManager;
    private final IdentityNotificationClient identityNotificationClient;
    private final IdentitySystemNotificationClient identitySystemNotificationClient;

    public PaymentReversalService(
            EntityManager entityManager,
            IdentityNotificationClient identityNotificationClient,
            IdentitySystemNotificationClient identitySystemNotificationClient) {

        this.entityManager = entityManager;
        this.identityNotificationClient = identityNotificationClient;
        this.identitySystemNotificationClient = identitySystemNotificationClient;
    }

    @Transactional
    public PaymentReversalResponse reverse(
            Long adminUserId,
            String bearerToken,
            Long paymentOrderId,
            String reason) {

        if (!paymentOrderExists(paymentOrderId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payment order was not found."
            );
        }

        Long customerUserId = customerUserIdForPayment(paymentOrderId);

        String safeReason = reason == null
                ? ""
                : reason.trim().replaceAll("\\s+", " ");

        StoredProcedureQuery query = entityManager.createStoredProcedureQuery(
                "pkg_payment.reverse_payment_order"
        );

        query.registerStoredProcedureParameter(
                "p_admin_user_id",
                Long.class,
                ParameterMode.IN
        );

        query.registerStoredProcedureParameter(
                "p_payment_order_id",
                Long.class,
                ParameterMode.IN
        );

        query.registerStoredProcedureParameter(
                "p_reason",
                String.class,
                ParameterMode.IN
        );

        query.setParameter("p_admin_user_id", adminUserId);
        query.setParameter("p_payment_order_id", paymentOrderId);
        query.setParameter("p_reason", safeReason);

        try {
            query.execute();
        } catch (RuntimeException exception) {
            throwFriendlyReversalError(exception);
            throw exception;
        }

        entityManager.clear();

        Object[] row = (Object[]) entityManager.createNativeQuery("""
                SELECT payment_order_id,
                       reference_number,
                       reversal_reference_number,
                       amount,
                       status,
                       reversed_at
                  FROM payment_orders
                 WHERE payment_order_id = :paymentOrderId
                """)
                .setParameter("paymentOrderId", paymentOrderId)
                .getSingleResult();

        String status = String.valueOf(row[4]);

        if (!"REVERSED".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment reversal was not completed."
            );
        }

        Long reversedPaymentOrderId = ((Number) row[0]).longValue();
        String referenceNumber = (String) row[1];
        String reversalReferenceNumber = (String) row[2];
        BigDecimal amount = (BigDecimal) row[3];
        LocalDateTime reversedAt = toLocalDateTime(row[5]);

        /*
         * Identity owns customer notifications and email addresses.
         * Its /internal/notifications/admin endpoint now creates both
         * the in-app notification and the customer email.
         */
        identityNotificationClient.createAdminNotification(
                bearerToken,
                customerUserId,
                "PAYMENT",
                "Your payment of INR "
                        + amount
                        + " was reversed. Reference: "
                        + referenceNumber
                        + ". Reason: "
                        + safeReason
        );

        identitySystemNotificationClient.createAuditEvent(
                adminUserId,
                "PAYMENT_REVERSED",
                "PAYMENT_ORDER",
                String.valueOf(reversedPaymentOrderId)
        );

        return new PaymentReversalResponse(
                reversedPaymentOrderId,
                referenceNumber,
                reversalReferenceNumber,
                amount,
                status,
                reversedAt,
                "Payment reversal completed successfully."
        );
    }

    private Long customerUserIdForPayment(Long paymentOrderId) {
        Number customerUserId = (Number) entityManager.createNativeQuery("""
                SELECT a.user_id
                  FROM payment_orders p
                  JOIN accounts a
                    ON a.account_id = p.source_account_id
                 WHERE p.payment_order_id = :paymentOrderId
                """)
                .setParameter("paymentOrderId", paymentOrderId)
                .getSingleResult();

        return customerUserId.longValue();
    }

    private boolean paymentOrderExists(Long paymentOrderId) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                  FROM payment_orders
                 WHERE payment_order_id = :paymentOrderId
                """)
                .setParameter("paymentOrderId", paymentOrderId)
                .getSingleResult();

        return count.longValue() == 1;
    }

    private void throwFriendlyReversalError(
            RuntimeException exception) {

        String message = databaseMessage(exception);

        if (message.contains("ORA-20315")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only successful payments can be reversed."
            );
        }

        if (message.contains("ORA-20316")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reversal reason must contain 5 to 500 characters."
            );
        }

        if (message.contains("ORA-20318")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The internal transfer cannot be reversed because "
                            + "the receiving account has insufficient balance."
            );
        }
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }

        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }

        throw new IllegalStateException(
                "Unsupported reversed_at value: "
                        + value.getClass().getName()
        );
    }

    private String databaseMessage(Throwable exception) {
        StringBuilder messages = new StringBuilder();
        Throwable current = exception;

        while (current != null) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append(' ');
            }

            current = current.getCause();
        }

        return messages.toString().toUpperCase(Locale.ROOT);
    }
}