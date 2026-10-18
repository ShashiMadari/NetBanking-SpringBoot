package com.inetbank.banking.adminbeneficiary;

import java.time.LocalDateTime;

import com.inetbank.banking.beneficiary.Beneficiary;

public record PendingBeneficiaryResponse(
        Long id,
        Long customerId,
        String beneficiaryName,
        String beneficiaryAccountNumber,
        String ifscCode,
        String bankName,
        String status,
        LocalDateTime createdAt
) {
    static PendingBeneficiaryResponse from(Beneficiary beneficiary) {
        return new PendingBeneficiaryResponse(
                beneficiary.getId(),
                beneficiary.getUserId(),
                beneficiary.getBeneficiaryName(),
                beneficiary.getBeneficiaryAccountNumber(),
                beneficiary.getIfscCode(),
                beneficiary.getBankName(),
                beneficiary.getStatus(),
                beneficiary.getCreatedAt()
        );
    }
}