package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreditCardMonthlyStatementResponse(
        Long cardId,
        String cardLastFour,
        int year,
        int month,
        LocalDate statementFrom,
        LocalDate statementTo,
        BigDecimal currentOutstandingAmount,
        BigDecimal minimumDueAmount,
        LocalDate paymentDueDate,
        BigDecimal totalMerchantCharges,
        BigDecimal totalRepayments,
        BigDecimal totalBillPayments,
        BigDecimal totalEmiConversions,
        BigDecimal totalEmiPayments,
        BigDecimal totalReversals,
        List<CreditCardMonthlyStatementEntryResponse> entries
) {
}