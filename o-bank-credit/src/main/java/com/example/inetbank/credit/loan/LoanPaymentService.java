package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.inetbank.credit.integration.BankingLoanPaymentClient;
import com.example.inetbank.credit.integration.IdentityNotificationClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;

@Service
public class LoanPaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(
            LoanPaymentService.class
    );

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LoanPaymentOrderRepository paymentOrders;
    private final LoanPaymentWriteRepository loanWrites;
    private final BankingLoanPaymentClient bankingClient;
    private final PasswordEncoder passwordEncoder;
    private final LoanPaymentEmailService emailService;
    private final IdentityNotificationClient notifications;
    private final IdentitySystemNotificationClient audits;

    public LoanPaymentService(
            LoanPaymentOrderRepository paymentOrders,
            LoanPaymentWriteRepository loanWrites,
            BankingLoanPaymentClient bankingClient,
            PasswordEncoder passwordEncoder,
            LoanPaymentEmailService emailService,
            IdentityNotificationClient notifications,
            IdentitySystemNotificationClient audits) {

        this.paymentOrders = paymentOrders;
        this.loanWrites = loanWrites;
        this.bankingClient = bankingClient;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.notifications = notifications;
        this.audits = audits;
    }

    @Transactional
    public LoanPaymentOtpResponse initiate(
            Long userId,
            String customerEmail,
            String bearerToken,
            InitiateLoanPaymentRequest request) {

        LoanPaymentWriteRepository.LoanPaymentLoan loan =
                activeLoan(request.loanId(), userId);

        String paymentType = request.paymentType()
                .trim()
                .toUpperCase(Locale.ROOT);

        validatePayment(
                loan,
                paymentType,
                request.amount()
        );

        String otp = otp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        String referenceNumber = referenceNumber();

        Long paymentOrderId = paymentOrders.create(
                request.sourceAccountId(),
                loan.loanId(),
                paymentType,
                request.amount(),
                referenceNumber,
                UUID.randomUUID().toString(),
                passwordEncoder.encode(otp),
                expiresAt
        );

        emailService.sendOtp(
                customerEmail,
                loan.loanAccountNumber(),
                paymentType,
                request.amount(),
                referenceNumber,
                otp
        );

        notifications.createNotification(
                bearerToken,
                "LOAN",
                "OTP sent for your loan "
                        + paymentType.toLowerCase(Locale.ROOT)
                        + " payment of INR "
                        + request.amount().toPlainString()
                        + "."
        );

        audits.createAuditEvent(
                userId,
                "LOAN_PAYMENT_OTP_SENT",
                "LOAN_PAYMENT_ORDER",
                String.valueOf(paymentOrderId)
        );

        return new LoanPaymentOtpResponse(
                paymentOrderId,
                loan.loanId(),
                paymentType,
                request.amount(),
                referenceNumber,
                "OTP_PENDING",
                expiresAt,
                "Loan payment OTP was sent to your email. "
                        + "It expires in 5 minutes."
        );
    }

    @Transactional
    public LoanPaymentOtpResponse resendOtp(
            Long userId,
            String customerEmail,
            String bearerToken,
            Long paymentOrderId) {

        LoanPaymentOrderRepository.LoanPaymentOrderDetails order =
                pendingOrder(paymentOrderId, userId);

        String otp = otp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        if (!paymentOrders.replaceOtp(
                paymentOrderId,
                passwordEncoder.encode(otp),
                expiresAt)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Loan payment is no longer awaiting OTP confirmation."
            );
        }

        emailService.sendOtp(
                customerEmail,
                order.loanAccountNumber(),
                order.paymentType(),
                order.amount(),
                order.referenceNumber(),
                otp
        );

        notifications.createNotification(
                bearerToken,
                "LOAN",
                "A new OTP was sent for your loan payment. "
                        + "Reference: "
                        + order.referenceNumber()
        );

        audits.createAuditEvent(
                userId,
                "LOAN_PAYMENT_OTP_RESENT",
                "LOAN_PAYMENT_ORDER",
                String.valueOf(paymentOrderId)
        );

        return new LoanPaymentOtpResponse(
                paymentOrderId,
                order.loanId(),
                order.paymentType(),
                order.amount(),
                order.referenceNumber(),
                "OTP_PENDING",
                expiresAt,
                "A new loan payment OTP was sent to your email."
        );
    }

    @Transactional
    public LoanPaymentConfirmationResponse confirm(
            Long userId,
            String customerEmail,
            String bearerToken,
            Long paymentOrderId,
            ConfirmLoanPaymentRequest request) {

        LoanPaymentOrderRepository.LoanPaymentOrderDetails order =
                pendingOrder(paymentOrderId, userId);

        if (order.otpExpiry() == null
                || !order.otpExpiry().isAfter(LocalDateTime.now())) {

            paymentOrders.markExpired(paymentOrderId);

            safeAudit(
                    userId,
                    "LOAN_PAYMENT_OTP_EXPIRED",
                    "LOAN_PAYMENT_ORDER",
                    paymentOrderId
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The loan payment OTP has expired. "
                            + "Start a new payment."
            );
        }

        if (!passwordEncoder.matches(
                request.otp(),
                order.otpHash())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid loan payment OTP."
            );
        }

        LoanPaymentWriteRepository.LoanPaymentLoan loan =
                activeLoan(order.loanId(), userId);

        PaymentCalculation calculation = validatePayment(
                loan,
                order.paymentType(),
                order.amount()
        );

        /*
         * Banking uses the reference number as its idempotency key.
         * A confirmation retry therefore cannot debit twice.
         */
        bankingClient.debit(
                userId,
                order.sourceAccountId(),
                order.paymentType(),
                order.amount(),
                order.referenceNumber()
        );

        Long repaymentId = loanWrites.createRepayment(
                order.paymentOrderId(),
                loan.loanId(),
                order.sourceAccountId(),
                order.paymentType(),
                order.amount(),
                loan.outstandingAmount(),
                calculation.outstandingAfter(),
                calculation.principalComponent(),
                calculation.interestComponent(),
                order.referenceNumber()
        );

        completeLoanPayment(
                loan,
                order,
                calculation,
                repaymentId
        );

        if (!paymentOrders.markSuccess(paymentOrderId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Loan payment order could not be completed."
            );
        }

        emailService.sendSuccess(
                customerEmail,
                loan.loanAccountNumber(),
                order.paymentType(),
                order.amount(),
                order.referenceNumber()
        );

        safeNotification(
                bearerToken,
                "LOAN",
                "Loan "
                        + order.paymentType()
                                .toLowerCase(Locale.ROOT)
                        + " payment of INR "
                        + order.amount().toPlainString()
                        + " was completed successfully. Reference: "
                        + order.referenceNumber()
        );

        safeAudit(
                userId,
                "LOAN_" + order.paymentType() + "_COMPLETED",
                "LOAN_PAYMENT_ORDER",
                paymentOrderId
        );

        return new LoanPaymentConfirmationResponse(
                paymentOrderId,
                order.loanId(),
                order.paymentType(),
                order.amount(),
                order.referenceNumber(),
                "SUCCESS",
                LocalDateTime.now(),
                "Loan payment completed successfully."
        );
    }

    private void completeLoanPayment(
            LoanPaymentWriteRepository.LoanPaymentLoan loan,
            LoanPaymentOrderRepository.LoanPaymentOrderDetails order,
            PaymentCalculation calculation,
            Long repaymentId) {

        switch (order.paymentType()) {
            case "EMI" -> loanWrites.completeEmiPayment(
                    loan.loanId(),
                    loan.customerUserId(),
                    calculation.schedule(),
                    repaymentId
            );

            case "PART_PAYMENT" -> loanWrites.completePartPayment(
                    loan.loanId(),
                    loan.customerUserId(),
                    calculation.outstandingAfter()
            );

            case "FORECLOSURE" -> loanWrites.completeForeclosure(
                    loan.loanId(),
                    loan.customerUserId()
            );

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid loan payment type."
            );
        }
    }

    private LoanPaymentWriteRepository.LoanPaymentLoan activeLoan(
            Long loanId,
            Long userId) {

        LoanPaymentWriteRepository.LoanPaymentLoan loan =
                loanWrites.lockActiveLoan(loanId, userId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Loan was not found for this customer."
                        ));

        if (!"ACTIVE".equals(loan.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an active loan can receive payments."
            );
        }

        return loan;
    }

    private LoanPaymentOrderRepository.LoanPaymentOrderDetails
            pendingOrder(
                    Long paymentOrderId,
                    Long userId) {

        LoanPaymentOrderRepository.LoanPaymentOrderDetails order =
                paymentOrders.findById(paymentOrderId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Loan payment order was not found."
                        ));

        if (!order.customerUserId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This loan payment does not belong to you."
            );
        }

        if (!"OTP_PENDING".equals(order.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This loan payment is no longer awaiting OTP confirmation."
            );
        }

        return order;
    }

    private PaymentCalculation validatePayment(
            LoanPaymentWriteRepository.LoanPaymentLoan loan,
            String paymentType,
            BigDecimal amount) {

        return switch (paymentType) {
            case "EMI" -> emiCalculation(loan, amount);

            case "PART_PAYMENT" -> {
                if (amount.compareTo(loan.outstandingAmount()) >= 0) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Use FORECLOSURE to pay the full outstanding amount."
                    );
                }

                yield new PaymentCalculation(
                        loan.outstandingAmount().subtract(amount),
                        amount,
                        BigDecimal.ZERO,
                        null
                );
            }

            case "FORECLOSURE" -> {
                if (amount.compareTo(loan.outstandingAmount()) != 0) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Foreclosure amount must equal the current "
                                    + "loan outstanding amount."
                    );
                }

                yield new PaymentCalculation(
                        BigDecimal.ZERO,
                        amount,
                        BigDecimal.ZERO,
                        null
                );
            }

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid loan payment type."
            );
        };
    }

    private PaymentCalculation emiCalculation(
            LoanPaymentWriteRepository.LoanPaymentLoan loan,
            BigDecimal amount) {

        LoanPaymentWriteRepository.DueEmiSchedule schedule =
                loanWrites.lockNextDueEmi(loan.loanId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "No due EMI installment exists for this loan."
                        ));

        if (amount.compareTo(schedule.emiAmount()) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "EMI payment amount must be exactly INR "
                            + schedule.emiAmount().toPlainString()
                            + "."
            );
        }

        return new PaymentCalculation(
                schedule.closingPrincipal(),
                schedule.principalComponent(),
                schedule.interestComponent(),
                schedule
        );
    }

    private void safeNotification(
            String bearerToken,
            String eventType,
            String message) {

        try {
            notifications.createNotification(
                    bearerToken,
                    eventType,
                    message
            );
        } catch (RuntimeException exception) {
            LOG.error("Loan notification could not be created.", exception);
        }
    }

    private void safeAudit(
            Long userId,
            String action,
            String entityType,
            Long entityId) {

        try {
            audits.createAuditEvent(
                    userId,
                    action,
                    entityType,
                    String.valueOf(entityId)
            );
        } catch (RuntimeException exception) {
            LOG.error("Loan audit event could not be created.", exception);
        }
    }

    private String otp() {
        return String.format(
                "%06d",
                RANDOM.nextInt(1_000_000)
        );
    }

    private String referenceNumber() {
        return "LOANPAY-"
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 16)
                        .toUpperCase(Locale.ROOT);
    }

    private record PaymentCalculation(
            BigDecimal outstandingAfter,
            BigDecimal principalComponent,
            BigDecimal interestComponent,
            LoanPaymentWriteRepository.DueEmiSchedule schedule) {
    }
}