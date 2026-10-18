package com.inetbank.banking.adminpayment;

import java.util.List;

public record AdminPaymentPageResponse(
        String status,
        String type,
        int page,
        int pageSize,
        long totalPayments,
        int totalPages,
        List<AdminPaymentResponse> payments
) {
}