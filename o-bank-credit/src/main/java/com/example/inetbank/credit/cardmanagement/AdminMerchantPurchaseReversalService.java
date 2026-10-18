package com.example.inetbank.credit.cardmanagement;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.inetbank.credit.integration.IdentityCustomerEmailClient;
import com.example.inetbank.credit.integration.IdentityCustomerEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.inetbank.credit.integration.IdentityScheduledNotificationClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;

@Service
public class AdminMerchantPurchaseReversalService {

    private final AdminMerchantPurchaseReversalRepository reversals;
    private final IdentityCustomerEmailClient customerEmailClient;
    private final MerchantPurchaseReversalEmailService emailService;
    private static final Logger LOG = LoggerFactory.getLogger(
            AdminMerchantPurchaseReversalService.class
    );

    private final IdentityScheduledNotificationClient notifications;
    private final IdentitySystemNotificationClient audits;
    public AdminMerchantPurchaseReversalService(
            AdminMerchantPurchaseReversalRepository reversals,
            IdentityCustomerEmailClient customerEmailClient,
            MerchantPurchaseReversalEmailService emailService,
            IdentityScheduledNotificationClient notifications,
            IdentitySystemNotificationClient audits) {

        this.reversals = reversals;
        this.customerEmailClient = customerEmailClient;
        this.emailService = emailService;
        this.notifications = notifications;
        this.audits = audits;
    }

    @Transactional
    public MerchantPurchaseReversalResponse reverse(
            Long adminUserId,
            Long merchantPurchaseId,
            MerchantPurchaseReversalRequest request) {

        try {
            reversals.reverse(
                    adminUserId,
                    merchantPurchaseId,
                    request.reason().trim()
            );
        } catch (DataAccessException exception) {
            throw mapDatabaseError(exception);
        }

        AdminMerchantPurchaseReversalRepository.ReversalDetails
                purchase = reversals.findById(merchantPurchaseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Merchant purchase was not found."
                ));

        IdentityCustomerEmailResponse customer =
                customerEmailClient.getCustomerContact(
                        purchase.customerUserId()
                );

        emailService.send(
                customer.email(),
                customer.fullName(),
                purchase.cardLastFour(),
                purchase.referenceNumber(),
                purchase.reversalReferenceNumber(),
                purchase.cardChargeAmount(),
                purchase.pointsRedeemed(),
                purchase.reversalReason()
        );

        try {
                notifications.createNotification(
                        purchase.customerUserId(),
                        "PAYMENT",
                        "Your credit-card merchant purchase was reversed. "
                                + "Card: XXXX"
                                + purchase.cardLastFour()
                                + ". Reference: "
                                + purchase.referenceNumber()
                );
            } catch (RuntimeException exception) {
                LOG.error(
                        "Could not create merchant-reversal notification. "
                                + "merchantPurchaseId={}",
                        purchase.merchantPurchaseId(),
                        exception
                );
            }
        try {
            audits.createAuditEvent(
                    adminUserId,
                    "CARD_MERCHANT_PURCHASE_REVERSED",
                    "MERCHANT_PURCHASE",
                    String.valueOf(purchase.merchantPurchaseId())
            );
        } catch (RuntimeException exception) {
            LOG.error(
                    "Could not create merchant-reversal audit event. "
                            + "merchantPurchaseId={}",
                    purchase.merchantPurchaseId(),
                    exception
            );
        }

        return new MerchantPurchaseReversalResponse(
                purchase.merchantPurchaseId(),
                purchase.referenceNumber(),
                purchase.reversalReferenceNumber(),
                purchase.cardChargeAmount(),
                purchase.pointsRedeemed(),
                purchase.pointsEarned(),
                purchase.status(),
                purchase.reversedAt(),
                "Merchant purchase reversed successfully."
        );
    }

    private ResponseStatusException mapDatabaseError(
            DataAccessException exception) {

        String message = exception.getMessage() == null
                ? ""
                : exception.getMessage();

        if (message.contains("ORA-20410")) {
            return new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Merchant purchase was not found."
            );
        }

        if (message.contains("ORA-20411")) {
            return new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reversal reason must contain 5 to 500 characters."
            );
        }

        if (message.contains("ORA-20413")) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only successful merchant purchases can be reversed."
            );
        }

        if (message.contains("ORA-20414")) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Card balance cannot be reversed for this purchase."
            );
        }

        if (message.contains("ORA-20415")) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reward-point balance cannot be adjusted for this purchase."
            );
        }

        throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Merchant purchase reversal could not be completed."
        );
    }
}