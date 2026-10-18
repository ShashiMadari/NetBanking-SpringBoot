package com.inetbank.banking.fundtransfer;

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

import com.inetbank.banking.beneficiary.Beneficiary;
import com.inetbank.banking.beneficiary.BeneficiaryRepository;
import com.inetbank.banking.integration.IdentityNotificationClient;
import com.inetbank.banking.integration.IdentitySystemNotificationClient;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;

@Service
public class TransferService {

    private static final int OTP_MINUTES = 5;

    private final EntityManager entityManager;
    private final BeneficiaryRepository beneficiaries;
    private final PaymentOrderRepository paymentOrders;
    private final TransferEmailService transferEmailService;
    private final IdentityNotificationClient identityNotificationClient;
    private final SecureRandom random = new SecureRandom();
    private final IdentitySystemNotificationClient identitySystemNotificationClient;
        private final RecipientTransferNotificationService recipientTransferNotifications;
    TransferService(
            EntityManager entityManager,
            BeneficiaryRepository beneficiaries,
            PaymentOrderRepository paymentOrders,
            TransferEmailService transferEmailService,
            IdentityNotificationClient identityNotificationClient,
            IdentitySystemNotificationClient identitySystemNotificationClient,
            RecipientTransferNotificationService recipientTransferNotifications) {

        this.entityManager = entityManager;
        this.beneficiaries = beneficiaries;
        this.paymentOrders = paymentOrders;
        this.transferEmailService = transferEmailService;
        this.identityNotificationClient = identityNotificationClient;
        this.identitySystemNotificationClient = identitySystemNotificationClient;
        this.recipientTransferNotifications = recipientTransferNotifications;
    }

    @Transactional
    public TransferOtpResponse initiate(
            Long userId,
            String customerEmail,
            InitiateTransferRequest request) {

        Beneficiary beneficiary = beneficiaries.findById(
                request.beneficiaryId()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Beneficiary not found."
        ));

        if (!beneficiary.getUserId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This beneficiary does not belong to you."
            );
        }

        if (!"ACTIVE".equals(beneficiary.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an approved beneficiary can receive a transfer."
            );
        }

        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusMinutes(OTP_MINUTES);

        String referenceNumber = "TRF-"
                + UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 16)
                        .toUpperCase(Locale.ROOT);

        Long paymentOrderId = createPaymentOrder(
                userId,
                request.sourceAccountId(),
                beneficiary.getId(),
                request.amount(),
                sha256(otp),
                expiresAt,
                referenceNumber,
                UUID.randomUUID().toString()
        );

        PaymentOrder paymentOrder = paymentOrders.findById(paymentOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Transfer order could not be created."
                ));

        transferEmailService.sendTransferOtp(
                customerEmail,
                otp,
                paymentOrder.getAmount(),
                paymentOrder.getReferenceNumber()
        );

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "TRANSFER_INITIATED",
                "PAYMENT_ORDER",
                String.valueOf(paymentOrder.getId())
        );

        return new TransferOtpResponse(
                paymentOrder.getId(),
                paymentOrder.getReferenceNumber(),
                paymentOrder.getAmount(),
                paymentOrder.getStatus(),
                paymentOrder.getOtpExpiry(),
                "Transfer OTP was sent to your email. It expires in 5 minutes."
        );
    }

    

    @Transactional
    public TransferOtpResponse resendOtp(
            Long userId,
            String customerEmail,
            Long paymentOrderId) {

        PaymentOrder paymentOrder = paymentOrders.findById(paymentOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transfer order not found."
                ));

        verifyOrderOwnership(userId, paymentOrderId);

        if (!"OTP_PENDING".equals(paymentOrder.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This transfer order is already processed."
            );
        }

        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusMinutes(OTP_MINUTES);

        int updated = entityManager.createNativeQuery("""
                UPDATE payment_orders
                   SET otp_hash = :otpHash,
                       otp_expiry = :expiry
                 WHERE payment_order_id = :paymentOrderId
                   AND payment_type = 'TRANSFER'
                   AND status = 'OTP_PENDING'
                """)
                .setParameter("otpHash", sha256(otp))
                .setParameter("expiry", Timestamp.valueOf(expiresAt))
                .setParameter("paymentOrderId", paymentOrderId)
                .executeUpdate();

        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A new OTP could not be generated for this transfer."
            );
        }

        transferEmailService.sendTransferOtp(
                customerEmail,
                otp,
                paymentOrder.getAmount(),
                paymentOrder.getReferenceNumber()
        );

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "TRANSFER_OTP_RESENT",
                "PAYMENT_ORDER",
                String.valueOf(paymentOrder.getId())
        );

        return new TransferOtpResponse(
                paymentOrder.getId(),
                paymentOrder.getReferenceNumber(),
                paymentOrder.getAmount(),
                "OTP_PENDING",
                expiresAt,
                "A new transfer OTP was sent to your email. It expires in 5 minutes."
        );
    }

    @Transactional
    public TransferConfirmationResponse confirm(
            Long userId,
            String customerEmail,
            String bearerToken,
            Long paymentOrderId,
            ConfirmTransferRequest request) {

        PaymentOrder paymentOrder = paymentOrders.findById(paymentOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transfer order not found."
                ));

        verifyOrderOwnership(userId, paymentOrderId);

        approvePaymentOrder(
                userId,
                paymentOrderId,
                sha256(request.otp())
        );

        entityManager.clear();

        PaymentOrder completedOrder = paymentOrders.findById(paymentOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Transfer order could not be reloaded."
                ));

        if (!"SUCCESS".equals(completedOrder.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transfer was not completed."
            );
        }

        transferEmailService.sendTransferSuccess(
                customerEmail,
                completedOrder.getAmount(),
                completedOrder.getReferenceNumber()
        );

        identityNotificationClient.createNotification(
                bearerToken,
                "PAYMENT",
                "Your transfer of INR "
                        + completedOrder.getAmount()
                        + " was completed successfully. Reference: "
                        + completedOrder.getReferenceNumber()
        );

        Beneficiary beneficiary = beneficiaries.findById(
                completedOrder.getBeneficiaryId()
        ).orElse(null);

        if (beneficiary != null) {
        recipientTransferNotifications.notifyRecipient(
                beneficiary,
                completedOrder.getAmount(),
                completedOrder.getReferenceNumber()
        );
        }

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "TRANSFER_COMPLETED",
                "PAYMENT_ORDER",
                String.valueOf(completedOrder.getId())
        );

        return new TransferConfirmationResponse(
                completedOrder.getId(),
                completedOrder.getReferenceNumber(),
                completedOrder.getAmount(),
                completedOrder.getStatus(),
                completedOrder.getCompletedAt(),
                "Fund transfer completed successfully."
        );
    }

    private Long createPaymentOrder(
            Long userId,
            Long sourceAccountId,
            Long beneficiaryId,
            BigDecimal amount,
            String otpHash,
            LocalDateTime expiry,
            String referenceNumber,
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
        query.setParameter("p_beneficiary_id", beneficiaryId);
        query.setParameter("p_biller_id", null);
        query.setParameter("p_consumer_reference", null);
        query.setParameter("p_payment_type", "TRANSFER");
        query.setParameter("p_amount", amount);
        query.setParameter("p_otp_hash", otpHash);
        query.setParameter("p_otp_expiry", Timestamp.valueOf(expiry));
        query.setParameter("p_reference_number", referenceNumber);
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

    private void verifyOrderOwnership(
            Long userId,
            Long paymentOrderId) {

        Number count = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                  FROM payment_orders p
                  JOIN accounts a
                    ON a.account_id = p.source_account_id
                 WHERE p.payment_order_id = :paymentOrderId
                   AND a.user_id = :userId
                   AND p.payment_type = 'TRANSFER'
                """)
                .setParameter("paymentOrderId", paymentOrderId)
                .setParameter("userId", userId)
                .getSingleResult();

        if (count.longValue() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This transfer order does not belong to you."
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