package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.inetbank.credit.integration.BankingCreditCardEmiClient;
import com.example.inetbank.credit.integration.IdentityNotificationClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;

@Service
public class CreditCardEmiPaymentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CreditCardEmiPaymentRepository payments;
    private final BankingCreditCardEmiClient bankingClient;
    private final PasswordEncoder passwordEncoder;
    private final CreditCardEmiPaymentEmailService emailService;
    private final IdentityNotificationClient notifications;
    private final IdentitySystemNotificationClient audits;

    public CreditCardEmiPaymentService(
            CreditCardEmiPaymentRepository payments,
            BankingCreditCardEmiClient bankingClient,
            PasswordEncoder passwordEncoder,
            CreditCardEmiPaymentEmailService emailService,
            IdentityNotificationClient notifications,
            IdentitySystemNotificationClient audits) {

        this.payments = payments;
        this.bankingClient = bankingClient;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.notifications = notifications;
        this.audits = audits;
    }

    @Transactional
    public CreditCardEmiPaymentOtpResponse initiate(
            Long userId,
            String customerEmail,
            String bearerToken,
            InitiateCreditCardEmiPaymentRequest request) {

        CreditCardEmiPaymentRepository.PayableInstallment installment =
                payableInstallment(userId, request.emiPlanId());

        if (payments.hasPendingOrder(userId, request.emiPlanId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An EMI payment OTP is already pending for this plan."
            );
        }

        String otp = otp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        String referenceNumber = referenceNumber();

        Long paymentOrderId = payments.createOrder(
                request.sourceAccountId(),
                installment.emiPlanId(),
                installment.emiAmount(),
                referenceNumber,
                passwordEncoder.encode(otp),
                expiresAt
        );

        emailService.sendOtp(
                customerEmail,
                otp,
                installment.cardLastFour(),
                installment.emiAmount()
        );

        notifications.createNotification(
                bearerToken,
                "PAYMENT",
                "OTP sent for your credit-card EMI payment of INR "
                        + installment.emiAmount().toPlainString()
                        + "."
        );

        audits.createAuditEvent(
                userId,
                "CREDIT_CARD_EMI_PAYMENT_OTP_SENT",
                "CREDIT_CARD_EMI_PAYMENT_ORDER",
                String.valueOf(paymentOrderId)
        );

        return new CreditCardEmiPaymentOtpResponse(
                paymentOrderId,
                installment.emiPlanId(),
                referenceNumber,
                installment.emiAmount(),
                "OTP_PENDING",
                expiresAt,
                "Credit-card EMI payment OTP was sent to your email. "
                        + "It expires in 5 minutes."
        );
    }

    @Transactional
    public CreditCardEmiPaymentOtpResponse resendOtp(
            Long userId,
            String customerEmail,
            String bearerToken,
            Long paymentOrderId) {

        CreditCardEmiPaymentRepository.EmiPaymentOrder order =
                pendingOrder(userId, paymentOrderId);

        CreditCardEmiPaymentRepository.PayableInstallment installment =
                payableInstallment(userId, order.emiPlanId());

        ensureSameInstallmentAmount(order, installment);

        String otp = otp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        payments.replaceOtp(
                userId,
                paymentOrderId,
                passwordEncoder.encode(otp),
                expiresAt
        );

        emailService.sendOtp(
                customerEmail,
                otp,
                installment.cardLastFour(),
                order.amount()
        );

        notifications.createNotification(
                bearerToken,
                "PAYMENT",
                "A new OTP was sent for your credit-card EMI payment. "
                        + "Reference: "
                        + order.referenceNumber()
        );

        audits.createAuditEvent(
                userId,
                "CREDIT_CARD_EMI_PAYMENT_OTP_RESENT",
                "CREDIT_CARD_EMI_PAYMENT_ORDER",
                String.valueOf(paymentOrderId)
        );

        return new CreditCardEmiPaymentOtpResponse(
                paymentOrderId,
                order.emiPlanId(),
                order.referenceNumber(),
                order.amount(),
                "OTP_PENDING",
                expiresAt,
                "A new credit-card EMI payment OTP was sent to your email."
        );
    }

    @Transactional
    public CreditCardEmiPaymentConfirmationResponse confirm(
            Long userId,
            String customerEmail,
            String bearerToken,
            Long paymentOrderId,
            ConfirmCreditCardEmiPaymentRequest request) {

        CreditCardEmiPaymentRepository.EmiPaymentOrder order =
                pendingOrder(userId, paymentOrderId);

        if (order.otpExpiry() == null
                || order.otpExpiry().isBefore(LocalDateTime.now())) {

            payments.markExpired(userId, paymentOrderId);

            audits.createAuditEvent(
                    userId,
                    "CREDIT_CARD_EMI_PAYMENT_OTP_EXPIRED",
                    "CREDIT_CARD_EMI_PAYMENT_ORDER",
                    String.valueOf(paymentOrderId)
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The EMI payment OTP has expired. Start again."
            );
        }

        if (!passwordEncoder.matches(request.otp(), order.otpHash())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid EMI payment OTP."
            );
        }

        CreditCardEmiPaymentRepository.PayableInstallment installment =
                payableInstallment(userId, order.emiPlanId());

        ensureSameInstallmentAmount(order, installment);

        /*
         * Banking uses the reference number for idempotency.
         * A retry cannot debit the source account twice.
         */
        bankingClient.debit(
                userId,
                order.sourceAccountId(),
                order.amount(),
                order.referenceNumber()
        );

        BigDecimal remainingBefore =
                installment.remainingPrincipal();

        BigDecimal remainingAfter = remainingBefore
                .subtract(installment.principalComponent())
                .max(BigDecimal.ZERO);

        Long repaymentId = payments.createRepayment(
                paymentOrderId,
                installment.emiPlanId(),
                order.sourceAccountId(),
                order.amount(),
                remainingBefore,
                remainingAfter,
                installment.principalComponent(),
                installment.interestComponent(),
                order.referenceNumber()
        );

        if (!payments.markSchedulePaid(
                installment.scheduleId(),
                repaymentId)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The EMI installment was already processed."
            );
        }

        if (!payments.updatePlanAfterPayment(
                userId,
                installment.emiPlanId(),
                installment.principalComponent())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The EMI plan could not be updated."
            );
        }

        if (!payments.reduceCardOutstanding(
                userId,
                installment.cardId(),
                installment.principalComponent())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The credit-card balance could not be updated."
            );
        }

        if (!payments.completeOrder(userId, paymentOrderId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The EMI payment order could not be completed."
            );
        }

        LocalDateTime completedAt = LocalDateTime.now();

        emailService.sendSuccess(
                customerEmail,
                installment.cardLastFour(),
                order.amount(),
                order.referenceNumber()
        );

        notifications.createNotification(
                bearerToken,
                "PAYMENT",
                "Credit-card EMI payment of INR "
                        + order.amount().toPlainString()
                        + " was completed successfully. Reference: "
                        + order.referenceNumber()
        );

        audits.createAuditEvent(
                userId,
                "CREDIT_CARD_EMI_PAYMENT_COMPLETED",
                "CREDIT_CARD_EMI_PAYMENT_ORDER",
                String.valueOf(paymentOrderId)
        );

        return new CreditCardEmiPaymentConfirmationResponse(
                paymentOrderId,
                order.emiPlanId(),
                order.referenceNumber(),
                order.amount(),
                "SUCCESS",
                completedAt,
                "Credit-card EMI payment completed successfully."
        );
    }

    private CreditCardEmiPaymentRepository.PayableInstallment
            payableInstallment(
                    Long userId,
                    Long emiPlanId) {

        return payments.findNextPayableInstallment(
                userId,
                emiPlanId
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No pending EMI installment was found for this plan."
        ));
    }

    private CreditCardEmiPaymentRepository.EmiPaymentOrder
            pendingOrder(
                    Long userId,
                    Long paymentOrderId) {

        CreditCardEmiPaymentRepository.EmiPaymentOrder order =
                payments.findOrder(userId, paymentOrderId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "EMI payment order was not found."
                                )
                        );

        if (!"OTP_PENDING".equals(order.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This EMI payment order is no longer awaiting OTP."
            );
        }

        return order;
    }

    private void ensureSameInstallmentAmount(
            CreditCardEmiPaymentRepository.EmiPaymentOrder order,
            CreditCardEmiPaymentRepository.PayableInstallment installment) {

        if (order.amount().compareTo(
                installment.emiAmount()) != 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The next EMI installment has changed. Start a new payment."
            );
        }
    }

    private String otp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String referenceNumber() {
        return "CCEMI-"
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 16)
                        .toUpperCase();
    }
}