package com.inetbank.banking.customerpayment;

import java.util.List;

public record CustomerPaymentPageResponse(
        String status,
        String type,
        int page,
        int pageSize,
        long totalPayments,
        int totalPages,
        List<CustomerPaymentResponse> payments
) {
}