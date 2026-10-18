package com.inetbank.banking.adminpayment;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPaymentService {

    private static final Set<String> STATUSES = Set.of(
            "ALL",
            "PENDING",
            "OTP_PENDING",
            "SUCCESS",
            "FAILED",
            "REJECTED",
            "REVERSED"
    );

    private static final Set<String> TYPES = Set.of(
            "ALL",
            "TRANSFER",
            "BILL_PAYMENT"
    );

    private final AdminPaymentRepository payments;

    public AdminPaymentService(
            AdminPaymentRepository payments) {

        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public AdminPaymentPageResponse list(
            String status,
            String type,
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

        String selectedStatus = filter(status, STATUSES, "status");
        String selectedType = filter(type, TYPES, "type");

        long totalPayments = payments.count(
                selectedStatus,
                selectedType
        );

        List<AdminPaymentResponse> result = payments.findPage(
                selectedStatus,
                selectedType,
                page,
                size
        );

        int totalPages = totalPayments == 0
                ? 0
                : (int) Math.ceil(
                        (double) totalPayments / size
                );

        return new AdminPaymentPageResponse(
                selectedStatus,
                selectedType,
                page,
                size,
                totalPayments,
                totalPages,
                result
        );
    }

    @Transactional(readOnly = true)
    public AdminPaymentResponse details(
            Long paymentOrderId) {

        return payments.findById(paymentOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment order was not found."
                ));
    }

    private String filter(
            String value,
            Set<String> allowed,
            String label) {

        String selected = value == null || value.isBlank()
                ? "ALL"
                : value.trim().toUpperCase(Locale.ROOT);

        if (!allowed.contains(selected)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid payment " + label + "."
            );
        }

        return selected;
    }
}