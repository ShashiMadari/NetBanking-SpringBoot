package com.inetbank.banking.adminbeneficiary;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.inetbank.banking.beneficiary.Beneficiary;
import com.inetbank.banking.beneficiary.BeneficiaryRepository;
import com.inetbank.banking.integration.IdentityNotificationClient;
import com.inetbank.banking.integration.IdentitySystemNotificationClient;

@Service
public class AdminBeneficiaryService {

    private final BeneficiaryRepository beneficiaries;
    private final IdentityNotificationClient identityNotificationClient;
    private final IdentitySystemNotificationClient identitySystemNotificationClient;

    public AdminBeneficiaryService(
            BeneficiaryRepository beneficiaries,
            IdentityNotificationClient identityNotificationClient,
            IdentitySystemNotificationClient identitySystemNotificationClient) {

        this.beneficiaries = beneficiaries;
        this.identityNotificationClient = identityNotificationClient;
        this.identitySystemNotificationClient =
                identitySystemNotificationClient;
    }

    @Transactional(readOnly = true)
    public List<PendingBeneficiaryResponse> pending() {
        return beneficiaries.findByStatusOrderByCreatedAtAsc("PENDING")
                .stream()
                .map(PendingBeneficiaryResponse::from)
                .toList();
    }

    @Transactional
    public BeneficiaryApprovalResponse approve(
            Long id,
            Long adminUserId,
            String bearerToken) {

        Beneficiary beneficiary = get(id);

        try {
            beneficiary.approve();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        Beneficiary savedBeneficiary = beneficiaries.save(beneficiary);

        identityNotificationClient.createAdminNotification(
                bearerToken,
                savedBeneficiary.getUserId(),
                "BENEFICIARY",
                "Beneficiary "
                        + savedBeneficiary.getBeneficiaryName()
                        + " was approved. You can now transfer money."
        );

        identitySystemNotificationClient.createAuditEvent(
                adminUserId,
                "BENEFICIARY_APPROVED",
                "BENEFICIARY",
                String.valueOf(savedBeneficiary.getId())
        );

        return BeneficiaryApprovalResponse.from(
                savedBeneficiary,
                "Beneficiary approved successfully."
        );
    }

    private Beneficiary get(Long id) {
        return beneficiaries.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Beneficiary not found."
                ));
    }
}