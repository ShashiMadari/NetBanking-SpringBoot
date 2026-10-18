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

import com.example.inetbank.credit.integration.BankingCreditRepaymentClient;
import com.example.inetbank.credit.integration.IdentityNotificationClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;

@Service
public class CreditCardPaymentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CreditCardPaymentRepository payments;
    private final BankingCreditRepaymentClient bankingClient;
    private final PasswordEncoder passwordEncoder;
    private final CreditCardPaymentEmailService emailService;
    private final IdentityNotificationClient notifications;
    private final IdentitySystemNotificationClient audits;

    public CreditCardPaymentService(
            CreditCardPaymentRepository payments,
            BankingCreditRepaymentClient bankingClient,
            PasswordEncoder passwordEncoder,
            CreditCardPaymentEmailService emailService,
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
    public CreditCardPaymentOtpResponse initiate(
            Long userId,
            String customerEmail,
            String bearerToken,
            InitiateCreditCardPaymentRequest request) {

        CreditCardPaymentRepository.CardPaymentDetails card =
                activeCard(request.cardId(), userId);

        validateAmount(request.amount(), card.outstandingAmount());

        String otp = otp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        String referenceNumber = referenceNumber();

        Long paymentOrderId = payments.createOrder(
                userId,
                request.sourceAccountId(),
                card.cardId(),
                request.amount(),
                referenceNumber,
                passwordEncoder.encode(otp),
                expiresAt
        );

        emailService.sendOtp(
                customerEmail,
                otp,
                request.amount(),
                card.lastFour()
        );

        notifications.createNotification(
                bearerToken,
                "PAYMENT",
                "OTP sent for credit-card payment of INR "
                        + request.amount().toPlainString()
                        + " for card XXXX"
                        + card.lastFour()
                        + "."
        );

        audits.createAuditEvent(
                userId,
                "CREDIT_CARD_PAYMENT_OTP_SENT",
                "CREDIT_CARD_PAYMENT_ORDER",
                String.valueOf(paymentOrderId)
        );

        return new CreditCardPaymentOtpResponse(
                paymentOrderId,
                referenceNumber,
                request.amount(),
                "OTP_PENDING",
                expiresAt,
                "Credit-card payment OTP was sent to your email. "
                        + "It expires in 5 minutes."
        );
    }

    @Transactional
    public CreditCardPaymentOtpResponse resendOtp(
            Long userId,
            String customerEmail,
            String bearerToken,
            Long paymentOrderId) {

        CreditCardPaymentRepository.CreditCardPaymentOrder order =
                pendingOrder(paymentOrderId, userId);

        CreditCardPaymentRepository.CardPaymentDetails card =
                activeCard(order.cardId(), userId);

        String otp = otp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        payments.replaceOtp(
                paymentOrderId,
                userId,
                passwordEncoder.encode(otp),
                expiresAt
        );

        emailService.sendOtp(
                customerEmail,
                otp,
                order.amount(),
                card.lastFour()
        );

        notifications.createNotification(
                bearerToken,
                "PAYMENT",
                "A new OTP was sent for your credit-card payment. "
                        + "Reference: "
                        + order.referenceNumber()
        );

        audits.createAuditEvent(
                userId,
                "CREDIT_CARD_PAYMENT_OTP_RESENT",
                "CREDIT_CARD_PAYMENT_ORDER",
                String.valueOf(paymentOrderId)
        );

        return new CreditCardPaymentOtpResponse(
                paymentOrderId,
                order.referenceNumber(),
                order.amount(),
                "OTP_PENDING",
                expiresAt,
                "A new credit-card payment OTP was sent to your email."
        );
    }

    @Transactional
    public CreditCardPaymentConfirmationResponse confirm(
            Long userId,
            String customerEmail,
            String bearerToken,
            Long paymentOrderId,
            ConfirmCreditCardPaymentRequest request) {

        CreditCardPaymentRepository.CreditCardPaymentOrder order =
                pendingOrder(paymentOrderId, userId);

        if (order.otpExpiry() == null
                || order.otpExpiry().isBefore(LocalDateTime.now())) {

            payments.markExpired(paymentOrderId, userId);

            audits.createAuditEvent(
                    userId,
                    "CREDIT_CARD_PAYMENT_OTP_EXPIRED",
                    "CREDIT_CARD_PAYMENT_ORDER",
                    String.valueOf(paymentOrderId)
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The payment OTP has expired. Start a new payment."
            );
        }

        if (!passwordEncoder.matches(request.otp(), order.otpHash())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid payment OTP."
            );
        }

        CreditCardPaymentRepository.CardPaymentDetails card =
                activeCard(order.cardId(), userId);

        validateAmount(order.amount(), card.outstandingAmount());

        /*
         * Banking debits by a unique reference number.
         * If confirmation is retried, Banking does not debit twice.
         */
        bankingClient.debit(
                userId,
                order.sourceAccountId(),
                order.amount(),
                order.referenceNumber()
        );

        BigDecimal balanceBefore = card.outstandingAmount();
        BigDecimal balanceAfter =
                balanceBefore.subtract(order.amount());

        if (!payments.reduceOutstandingBalance(
                card.cardId(),
                userId,
                order.amount())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Card outstanding balance changed. Please try again."
            );
        }

        if (!payments.completeOrder(paymentOrderId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment order could not be completed."
            );
        }

        payments.createRepayment(
                paymentOrderId,
                card.cardId(),
                order.sourceAccountId(),
                order.amount(),
                balanceBefore,
                balanceAfter,
                order.referenceNumber()
        );

        LocalDateTime completedAt = LocalDateTime.now();

        emailService.sendSuccess(
                customerEmail,
                order.amount(),
                card.lastFour(),
                order.referenceNumber()
        );

        notifications.createNotification(
                bearerToken,
                "PAYMENT",
                "Credit-card payment of INR "
                        + order.amount().toPlainString()
                        + " was completed successfully. Reference: "
                        + order.referenceNumber()
        );

        audits.createAuditEvent(
                userId,
                "CREDIT_CARD_PAYMENT_COMPLETED",
                "CREDIT_CARD_PAYMENT_ORDER",
                String.valueOf(paymentOrderId)
        );

        return new CreditCardPaymentConfirmationResponse(
                paymentOrderId,
                order.referenceNumber(),
                order.amount(),
                "SUCCESS",
                completedAt,
                "Credit-card payment completed successfully."
        );
    }

    private CreditCardPaymentRepository.CardPaymentDetails activeCard(
            Long cardId,
            Long userId) {

        return payments.findActiveCreditCard(cardId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active credit card was not found."
                ));
    }

    private CreditCardPaymentRepository.CreditCardPaymentOrder pendingOrder(
            Long paymentOrderId,
            Long userId) {

        CreditCardPaymentRepository.CreditCardPaymentOrder order =
                payments.findOrder(paymentOrderId, userId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Credit-card payment order was not found."
                        ));

        if (!"OTP_PENDING".equals(order.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This payment order is no longer awaiting OTP confirmation."
            );
        }

        return order;
    }

    private void validateAmount(
            BigDecimal amount,
            BigDecimal outstandingAmount) {

        if (amount.compareTo(outstandingAmount) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount cannot exceed the card outstanding balance."
            );
        }
    }

    private String otp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String referenceNumber() {
        return "CCPAY-"
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 16)
                        .toUpperCase();
    }
}