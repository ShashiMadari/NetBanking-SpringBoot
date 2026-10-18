package com.inetbank.banking.billpayment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.inetbank.banking.fundtransfer.PaymentOrder;
import com.inetbank.banking.fundtransfer.PaymentOrderRepository;
import com.inetbank.banking.integration.IdentityNotificationClient;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import com.inetbank.banking.integration.IdentitySystemNotificationClient;

@Service
public class BillPaymentService {

    private static final int OTP_MINUTES = 5;

    private final EntityManager entityManager;
    private final BillerRepository billers;
    private final PaymentOrderRepository paymentOrders;
    private final BillPaymentEmailService emailService;
    private final IdentityNotificationClient identityNotificationClient;
    private final SecureRandom random = new SecureRandom();
    private final IdentitySystemNotificationClient identitySystemNotificationClient;

    public BillPaymentService(
            EntityManager entityManager,
            BillerRepository billers,
            PaymentOrderRepository paymentOrders,
            BillPaymentEmailService emailService,
            IdentityNotificationClient identityNotificationClient,
            IdentitySystemNotificationClient identitySystemNotificationClient) {

        this.entityManager = entityManager;
        this.billers = billers;
        this.paymentOrders = paymentOrders;
        this.emailService = emailService;
        this.identityNotificationClient = identityNotificationClient;
        this.identitySystemNotificationClient = identitySystemNotificationClient;
    }

    @Transactional
    public BillPaymentOtpResponse initiate(
            Long userId,
            String customerEmail,
            BillPaymentRequest request) {

        Biller biller = billers.findById(request.billerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Biller not found."
                ));

        if (!"ACTIVE".equals(biller.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This biller is not active."
            );
        }

        String otp = generateOtp();
        LocalDateTime expiry = LocalDateTime.now()
                .plusMinutes(OTP_MINUTES);

        String reference = "BILL-"
                + UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 16)
                        .toUpperCase(Locale.ROOT);

        Long paymentOrderId = createPaymentOrder(
                userId,
                request.sourceAccountId(),
                biller.getId(),
                request.consumerReference().trim(),
                request.amount(),
                sha256(otp),
                expiry,
                reference,
                UUID.randomUUID().toString()
        );

        PaymentOrder order = paymentOrders.findById(paymentOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Bill payment order could not be created."
                ));

        emailService.sendPaymentOtp(
                customerEmail,
                otp,
                biller.getBillerName(),
                order.getAmount(),
                order.getReferenceNumber()
        );

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "BILL_PAYMENT_INITIATED",
                "PAYMENT_ORDER",
                String.valueOf(order.getId())
        );

        return new BillPaymentOtpResponse(
                order.getId(),
                order.getReferenceNumber(),
                order.getAmount(),
                order.getStatus(),
                order.getOtpExpiry(),
                "Bill payment OTP was sent to your email. It expires in 5 minutes."
        );
    }

    @Transactional
    public BillPaymentOtpResponse resendOtp(
            Long userId,
            String customerEmail,
            Long paymentOrderId) {

        PaymentOrder order = paymentOrders.findById(paymentOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Bill payment order not found."
                ));

        verifyBillOrderOwnership(userId, paymentOrderId);

        if (!"OTP_PENDING".equals(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This bill payment order is already processed."
            );
        }

        Biller biller = billers.findById(getBillerId(paymentOrderId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Biller not found."
                ));

        if (!"ACTIVE".equals(biller.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This biller is not active."
            );
        }

        String otp = generateOtp();
        LocalDateTime expiry = LocalDateTime.now()
                .plusMinutes(OTP_MINUTES);

        int updated = entityManager.createNativeQuery("""
                UPDATE payment_orders
                   SET otp_hash = :otpHash,
                       otp_expiry = :expiry
                 WHERE payment_order_id = :paymentOrderId
                   AND payment_type = 'BILL_PAYMENT'
                   AND status = 'OTP_PENDING'
                """)
                .setParameter("otpHash", sha256(otp))
                .setParameter("expiry", Timestamp.valueOf(expiry))
                .setParameter("paymentOrderId", paymentOrderId)
                .executeUpdate();

        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A new OTP could not be generated for this bill payment."
            );
        }

        emailService.sendPaymentOtp(
                customerEmail,
                otp,
                biller.getBillerName(),
                order.getAmount(),
                order.getReferenceNumber()
        );

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "BILL_PAYMENT_OTP_RESENT",
                "PAYMENT_ORDER",
                String.valueOf(order.getId())
        );

        return new BillPaymentOtpResponse(
                order.getId(),
                order.getReferenceNumber(),
                order.getAmount(),
                "OTP_PENDING",
                expiry,
                "A new bill payment OTP was sent to your email. It expires in 5 minutes."
        );
    }

    @Transactional
    public BillPaymentConfirmationResponse confirm(
            Long userId,
            String customerEmail,
            String bearerToken,
            Long paymentOrderId,
            ConfirmBillPaymentRequest request) {

        PaymentOrder order = paymentOrders.findById(paymentOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Bill payment order not found."
                ));

        verifyBillOrderOwnership(userId, paymentOrderId);

        approvePaymentOrder(
                userId,
                paymentOrderId,
                sha256(request.otp())
        );

        entityManager.clear();

        PaymentOrder completed = paymentOrders.findById(paymentOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Bill payment order could not be reloaded."
                ));

        if (!"SUCCESS".equals(completed.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bill payment was not completed."
            );
        }

        Biller biller = billers.findById(getBillerId(paymentOrderId))
                .orElse(null);

        String billerName = biller == null
                ? "selected biller"
                : biller.getBillerName();

        emailService.sendPaymentSuccess(
                customerEmail,
                billerName,
                completed.getAmount(),
                completed.getReferenceNumber()
        );

        identityNotificationClient.createNotification(
                bearerToken,
                "PAYMENT",
                "Your bill payment to "
                        + billerName
                        + " of INR "
                        + completed.getAmount()
                        + " was completed successfully. Reference: "
                        + completed.getReferenceNumber()
        );

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "BILL_PAYMENT_COMPLETED",
                "PAYMENT_ORDER",
                String.valueOf(completed.getId())
        );

        return new BillPaymentConfirmationResponse(
                completed.getId(),
                completed.getReferenceNumber(),
                completed.getAmount(),
                completed.getStatus(),
                completed.getCompletedAt(),
                "Bill payment completed successfully."
        );
    }

    private Long createPaymentOrder(
            Long userId,
            Long sourceAccountId,
            Long billerId,
            String consumerReference,
            BigDecimal amount,
            String otpHash,
            LocalDateTime expiry,
            String reference,
            String idempotencyKey) {

        StoredProcedureQuery query = entityManager.createStoredProcedureQuery(
                "pkg_payment.create_payment_order"
        );

        query.registerStoredProcedureParameter(
                "p_user_id", Long.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_source_account_id", Long.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_beneficiary_id", Long.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_biller_id", Long.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_consumer_reference", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_payment_type", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_amount", BigDecimal.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_otp_hash", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_otp_expiry", Timestamp.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_reference_number", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_idempotency_key", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_payment_order_id", Long.class, ParameterMode.OUT);

        query.setParameter("p_user_id", userId);
        query.setParameter("p_source_account_id", sourceAccountId);
        query.setParameter("p_beneficiary_id", null);
        query.setParameter("p_biller_id", billerId);
        query.setParameter("p_consumer_reference", consumerReference);
        query.setParameter("p_payment_type", "BILL_PAYMENT");
        query.setParameter("p_amount", amount);
        query.setParameter("p_otp_hash", otpHash);
        query.setParameter("p_otp_expiry", Timestamp.valueOf(expiry));
        query.setParameter("p_reference_number", reference);
        query.setParameter("p_idempotency_key", idempotencyKey);

        query.execute();

        return ((Number) query.getOutputParameterValue(
                "p_payment_order_id"
        )).longValue();
    }

    private void approvePaymentOrder(
            Long userId,
            Long paymentOrderId,
            String otpHash) {

        StoredProcedureQuery query = entityManager.createStoredProcedureQuery(
                "pkg_payment.approve_payment_order"
        );

        query.registerStoredProcedureParameter(
                "p_user_id", Long.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_payment_order_id", Long.class, ParameterMode.IN);
        query.registerStoredProcedureParameter(
                "p_otp_hash", String.class, ParameterMode.IN);

        query.setParameter("p_user_id", userId);
        query.setParameter("p_payment_order_id", paymentOrderId);
        query.setParameter("p_otp_hash", otpHash);

        query.execute();
    }

    private Long getBillerId(Long paymentOrderId) {
        Object value = entityManager.createNativeQuery("""
                SELECT biller_id
                  FROM payment_orders
                 WHERE payment_order_id = :paymentOrderId
                   AND payment_type = 'BILL_PAYMENT'
                """)
                .setParameter("paymentOrderId", paymentOrderId)
                .getSingleResult();

        return ((Number) value).longValue();
    }

    private void verifyBillOrderOwnership(
            Long userId,
            Long paymentOrderId) {

        Number count = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                  FROM payment_orders p
                  JOIN accounts a
                    ON a.account_id = p.source_account_id
                 WHERE p.payment_order_id = :paymentOrderId
                   AND a.user_id = :userId
                   AND p.payment_type = 'BILL_PAYMENT'
                """)
                .setParameter("paymentOrderId", paymentOrderId)
                .setParameter("userId", userId)
                .getSingleResult();

        if (count.longValue() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This bill payment order does not belong to you."
            );
        }
    }

    private String generateOtp() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable.",
                    exception
            );
        }
    }
}