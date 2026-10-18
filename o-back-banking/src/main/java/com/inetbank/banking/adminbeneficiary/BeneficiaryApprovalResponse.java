package com.inetbank.banking.adminbeneficiary;

import com.inetbank.banking.beneficiary.Beneficiary;

public record BeneficiaryApprovalResponse(
        Long id,
        String beneficiaryName,
        String status,
        String message
) {
    static BeneficiaryApprovalResponse from(
            Beneficiary beneficiary,
            String message) {

        return new BeneficiaryApprovalResponse(
                beneficiary.getId(),
                beneficiary.getBeneficiaryName(),
                beneficiary.getStatus(),
                message
        );
    }
}