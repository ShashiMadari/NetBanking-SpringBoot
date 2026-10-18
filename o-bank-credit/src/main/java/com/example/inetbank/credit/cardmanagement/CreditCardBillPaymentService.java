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

import com.example.inetbank.credit.integration.BankingBillerClient;
import com.example.inetbank.credit.integration.IdentityNotificationClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;

@Service
public class CreditCardBillPaymentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CreditCardBillPaymentRepository payments;
    private final BankingBillerClient billers;
    private final PasswordEncoder passwordEncoder;
    private final CreditCardBillPaymentEmailService emailService;
    private final IdentityNotificationClient notifications;
    private final IdentitySystemNotificationClient audits;

    public CreditCardBillPaymentService(
            CreditCardBillPaymentRepository payments,
            BankingBillerClient billers,
            PasswordEncoder passwordEncoder,
            CreditCardBillPaymentEmailService emailService,
            IdentityNotificationClient notifications,
            IdentitySystemNotificationClient audits) {

        this.payments = payments;
        this.billers = billers;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.notifications = notifications;
        this.audits = audits;
    }

    @Transactional
    public CreditCardBillPaymentOtpResponse initiate(
            Long userId,
            String customerEmail,
            String bearerToken,
            InitiateCreditCardBillPaymentRequest request) {

        CreditCardBillPaymentRepository.CreditCardDetails card =
                activeCard(request.cardId(), userId);

        ensureAvailableCredit(
                request.amount(),
                card.creditLimit(),
                card.outstandingAmount()
        );

        BankingBillerClient.BankingBillerResponse biller =
                activeBiller(bearerToken, request.billerId());

        String otp = otp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        String referenceNumber = referenceNumber();

        Long paymentOrderId = payments.createOrder(
                userId,
                card.cardId(),
                biller.id(),
                clean(request.consumerReference()),
                request.amount(),
                referenceNumber,
                passwordEncoder.encode(otp),
                expiresAt
        );

        emailService.sendOtp(
                customerEmail,
                otp,
                card.lastFour(),
                biller.billerName(),
                clean(request.consumerReference()),
                request.amount()
        );

        notifications.createNotification(
                bearerToken,
                "BILL_PAYMENT",
                "OTP sent for credit-card bill payment of INR "
                        + request.amount().toPlainString()
                        + " to "
                        + biller.billerName()
                        + "."
        );

        audits.createAuditEvent(
                userId,
                "CREDIT_CARD_BILL_PAYMENT_OTP_SENT",
                "CREDIT_CARD_BILL_PAYMENT_ORDER",
                String.valueOf(paymentOrderId)
        );

        return new CreditCardBillPaymentOtpResponse(
                paymentOrderId,
                referenceNumber,
                request.amount(),
                "OTP_PENDING",
                expiresAt,
                "Credit-card bill payment OTP was sent to your email. "
                        + "It expires in 5 minutes."
        );
    }

    @Transactional
    public CreditCardBillPaymentOtpResponse resendOtp(
            Long userId,
            String customerEmail,
            String bearerToken,
            Long paymentOrderId) {

        CreditCardBillPaymentRepository.BillPaymentOrder order =
                pendingOrder(paymentOrderId, userId);

        CreditCardBillPaymentRepository.CreditCardDetails card =
                activeCard(order.cardId(), userId);

        BankingBillerClient.BankingBillerResponse biller =
                activeBiller(bearerToken, order.billerId());

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
                card.lastFour(),
                biller.billerName(),
                order.consumerReference(),
                order.amount()
        );

        notifications.createNotification(
                bearerToken,
                "BILL_PAYMENT",
                "A new OTP was sent for your credit-card bill payment. "
                        + "Reference: "
                        + order.referenceNumber()
        );

        audits.createAuditEvent(
                userId,
                "CREDIT_CARD_BILL_PAYMENT_OTP_RESENT",
                "CREDIT_CARD_BILL_PAYMENT_ORDER",
                String.valueOf(paymentOrderId)
        );

        return new CreditCardBillPaymentOtpResponse(
                paymentOrderId,
                order.referenceNumber(),
                order.amount(),
                "OTP_PENDING",
                expiresAt,
                "A new credit-card bill-payment OTP was sent to your email."
        );
    }

    @Transactional
    public CreditCardBillPaymentConfirmationResponse confirm(
            Long userId,
            String customerEmail,
            String bearerToken,
            Long paymentOrderId,
            ConfirmCreditCardBillPaymentRequest request) {

        CreditCardBillPaymentRepository.BillPaymentOrder order =
                pendingOrder(paymentOrderId, userId);

        if (order.otpExpiry() == null
                || order.otpExpiry().isBefore(LocalDateTime.now())) {

            payments.markExpired(paymentOrderId, userId);

            audits.createAuditEvent(
                    userId,
                    "CREDIT_CARD_BILL_PAYMENT_OTP_EXPIRED",
                    "CREDIT_CARD_BILL_PAYMENT_ORDER",
                    String.valueOf(paymentOrderId)
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The bill-payment OTP has expired. Start a new payment."
            );
        }

        if (!passwordEncoder.matches(request.otp(), order.otpHash())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid bill-payment OTP."
            );
        }

        CreditCardBillPaymentRepository.CreditCardDetails card =
                activeCard(order.cardId(), userId);

        BankingBillerClient.BankingBillerResponse biller =
                activeBiller(bearerToken, order.billerId());

        ensureAvailableCredit(
                order.amount(),
                card.creditLimit(),
                card.outstandingAmount()
        );

        BigDecimal outstandingBefore = card.outstandingAmount();
        BigDecimal outstandingAfter =
                outstandingBefore.add(order.amount());

        if (!payments.increaseOutstandingBalance(
                card.cardId(),
                userId,
                order.amount())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Available credit changed. Please try again."
            );
        }

        if (!payments.completeOrder(paymentOrderId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bill-payment order could not be completed."
            );
        }

        payments.createBillPayment(
                paymentOrderId,
                card.cardId(),
                biller.id(),
                order.consumerReference(),
                order.amount(),
                outstandingBefore,
                outstandingAfter,
                order.referenceNumber()
        );

        LocalDateTime completedAt = LocalDateTime.now();

        emailService.sendSuccess(
                customerEmail,
                card.lastFour(),
                biller.billerName(),
                order.consumerReference(),
                order.amount(),
                order.referenceNumber()
        );

        notifications.createNotification(
                bearerToken,
                "BILL_PAYMENT",
                "Credit-card bill payment of INR "
                        + order.amount().toPlainString()
                        + " to "
                        + biller.billerName()
                        + " was completed successfully. Reference: "
                        + order.referenceNumber()
        );

        audits.createAuditEvent(
                userId,
                "CREDIT_CARD_BILL_PAYMENT_COMPLETED",
                "CREDIT_CARD_BILL_PAYMENT_ORDER",
                String.valueOf(paymentOrderId)
        );

        return new CreditCardBillPaymentConfirmationResponse(
                paymentOrderId,
                order.referenceNumber(),
                order.amount(),
                "SUCCESS",
                completedAt,
                "Credit-card bill payment completed successfully."
        );
    }

    private CreditCardBillPaymentRepository.CreditCardDetails activeCard(
            Long cardId,
            Long userId) {

        return payments.findActiveCreditCard(cardId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active credit card was not found."
                ));
    }

    private BankingBillerClient.BankingBillerResponse activeBiller(
            String bearerToken,
            Long billerId) {

        try {
            return billers.activeBiller(bearerToken, billerId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        }
    }

    private CreditCardBillPaymentRepository.BillPaymentOrder pendingOrder(
            Long paymentOrderId,
            Long userId) {

        CreditCardBillPaymentRepository.BillPaymentOrder order =
                payments.findOrder(paymentOrderId, userId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Credit-card bill-payment order was not found."
                        ));

        if (!"OTP_PENDING".equals(order.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This bill-payment order is no longer awaiting OTP confirmation."
            );
        }

        return order;
    }

    private void ensureAvailableCredit(
            BigDecimal amount,
            BigDecimal creditLimit,
            BigDecimal outstandingAmount) {

        BigDecimal availableCredit =
                creditLimit.subtract(outstandingAmount);

        if (amount.compareTo(availableCredit) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount exceeds the available credit limit."
            );
        }
    }

    private String clean(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String otp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String referenceNumber() {
        return "CCBILL-"
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 16)
                        .toUpperCase();
    }
}