package com.inetbank.banking.fundtransfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferConfirmationResponse(
        Long paymentOrderId,
        String referenceNumber,
        BigDecimal amount,
        String status,
        LocalDateTime completedAt,
        String message
) {
}