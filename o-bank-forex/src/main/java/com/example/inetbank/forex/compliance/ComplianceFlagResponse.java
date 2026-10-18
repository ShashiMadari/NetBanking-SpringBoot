package com.example.inetbank.forex.compliance;

import java.time.LocalDateTime;

public record ComplianceFlagResponse(

        Long complianceFlagId,
        Long forexTransactionId,
        String ruleCode,
        String reason,
        String status,
        LocalDateTime createdAt,
        Long reviewedBy,
        LocalDateTime reviewedAt
) {
}