package com.example.inetbank.credit.cardmanagement;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.inetbank.credit.integration.IdentityCustomerEmailClient;
import com.example.inetbank.credit.integration.IdentityCustomerEmailResponse;

@Service
public class AdminMerchantPurchaseService {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "ALL",
            "SUCCESS",
            "REVERSED"
    );

    private final AdminMerchantPurchaseRepository purchases;
    private final IdentityCustomerEmailClient identityCustomerEmailClient;

    public AdminMerchantPurchaseService(
            AdminMerchantPurchaseRepository purchases,
            IdentityCustomerEmailClient identityCustomerEmailClient) {

        this.purchases = purchases;
        this.identityCustomerEmailClient =
                identityCustomerEmailClient;
    }

    @Transactional(readOnly = true)
    public AdminMerchantPurchasePageResponse list(
            String requestedStatus,
            Long merchantId,
            int page,
            int size) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater."
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100."
            );
        }

        String status = normalizeStatus(requestedStatus);

        long totalPurchases = purchases.count(
                status,
                merchantId
        );

        int totalPages = totalPurchases == 0
                ? 0
                : (int) Math.ceil(
                        (double) totalPurchases / size
                );

        List<AdminMerchantPurchaseResponse> result =
                addCustomerContacts(
                        purchases.findPage(
                                status,
                                merchantId,
                                page,
                                size
                        )
                );

        return new AdminMerchantPurchasePageResponse(
                status,
                merchantId,
                page,
                size,
                totalPurchases,
                totalPages,
                result
        );
    }

    @Transactional(readOnly = true)
    public AdminMerchantPurchaseResponse detail(
            Long merchantPurchaseId) {

        AdminMerchantPurchaseResponse purchase =
                purchases.findById(merchantPurchaseId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Merchant purchase was not found."
                                )
                        );

        return addCustomerContact(
                purchase,
                identityCustomerEmailClient.getCustomerContact(
                        purchase.customerUserId()
                )
        );
    }

    private List<AdminMerchantPurchaseResponse> addCustomerContacts(
            List<AdminMerchantPurchaseResponse> purchases) {

        Map<Long, IdentityCustomerEmailResponse> contacts =
                new HashMap<>();

        return purchases.stream()
                .map(purchase -> addCustomerContact(
                        purchase,
                        contacts.computeIfAbsent(
                                purchase.customerUserId(),
                                identityCustomerEmailClient
                                        ::getCustomerContact
                        )
                ))
                .toList();
    }

    private AdminMerchantPurchaseResponse addCustomerContact(
            AdminMerchantPurchaseResponse purchase,
            IdentityCustomerEmailResponse customer) {

        return new AdminMerchantPurchaseResponse(
                purchase.merchantPurchaseId(),
                purchase.referenceNumber(),
                purchase.status(),

                purchase.customerUserId(),
                customer.fullName(),
                customer.email(),

                purchase.cardId(),
                purchase.cardLastFour(),

                purchase.merchantId(),
                purchase.merchantCode(),
                purchase.merchantName(),
                purchase.merchantCategory(),

                purchase.purchaseAmount(),
                purchase.pointsRedeemed(),
                purchase.pointsDiscountAmount(),
                purchase.cardChargeAmount(),
                purchase.pointsEarned(),

                purchase.purchasedAt(),

                purchase.reversalReferenceNumber(),
                purchase.reversalReason(),
                purchase.reversedAt()
        );
    }

    private String normalizeStatus(String requestedStatus) {

        String status = requestedStatus == null
                || requestedStatus.isBlank()
                ? "ALL"
                : requestedStatus.trim()
                        .toUpperCase(Locale.ROOT);

        if (!ALLOWED_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status must be ALL, SUCCESS, or REVERSED."
            );
        }

        return status;
    }
}