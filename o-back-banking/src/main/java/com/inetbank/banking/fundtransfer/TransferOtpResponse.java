package com.inetbank.banking.fundtransfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferOtpResponse(
        Long paymentOrderId,
        String referenceNumber,
        BigDecimal amount,
        String status,
        LocalDateTime otpExpiresAt,
        String message
) {
}