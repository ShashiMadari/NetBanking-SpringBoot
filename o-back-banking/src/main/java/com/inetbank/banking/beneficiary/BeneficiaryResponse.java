package com.inetbank.banking.beneficiary;

import java.time.LocalDateTime;

public record BeneficiaryResponse(
        Long id,
        String beneficiaryName,
        String beneficiaryAccountNumber,
        String ifscCode,
        String bankName,
        String status,
        LocalDateTime createdAt
) {
    static BeneficiaryResponse from(Beneficiary beneficiary) {
        return new BeneficiaryResponse(
                beneficiary.getId(),
                beneficiary.getBeneficiaryName(),
                beneficiary.getBeneficiaryAccountNumber(),
                beneficiary.getIfscCode(),
                beneficiary.getBankName(),
                beneficiary.getStatus(),
                beneficiary.getCreatedAt()
        );
    }
}