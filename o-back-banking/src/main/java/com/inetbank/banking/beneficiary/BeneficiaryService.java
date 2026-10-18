package com.inetbank.banking.beneficiary;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.inetbank.banking.integration.IdentityNotificationClient;
import com.inetbank.banking.integration.IdentitySystemNotificationClient;

@Service
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaries;
    private final BeneficiaryEmailService beneficiaryEmailService;
    private final IdentityNotificationClient identityNotificationClient;
    private final IdentitySystemNotificationClient identitySystemNotificationClient;
    public BeneficiaryService(
            BeneficiaryRepository beneficiaries,
            BeneficiaryEmailService beneficiaryEmailService,
            IdentityNotificationClient identityNotificationClient,
            IdentitySystemNotificationClient identitySystemNotificationClient) {

        this.beneficiaries = beneficiaries;
        this.beneficiaryEmailService = beneficiaryEmailService;
        this.identityNotificationClient = identityNotificationClient;
        this.identitySystemNotificationClient = identitySystemNotificationClient;
    }

    @Transactional
    public BeneficiaryResponse add(
            Long userId,
            String customerEmail,
            String bearerToken,
            AddBeneficiaryRequest request) {

        String accountNumber = request.beneficiaryAccountNumber().trim();

        String ifscCode = request.ifscCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (beneficiaries
                .existsByUserIdAndBeneficiaryAccountNumberAndIfscCode(
                        userId, accountNumber, ifscCode)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This beneficiary is already registered for your account."
            );
        }

        Beneficiary beneficiary = new Beneficiary(
                userId,
                clean(request.beneficiaryName()),
                accountNumber,
                ifscCode,
                optionalClean(request.bankName())
        );

        Beneficiary savedBeneficiary = beneficiaries.save(beneficiary);

        identityNotificationClient.createNotification(
                bearerToken,
                "BENEFICIARY",
                "Beneficiary "
                        + savedBeneficiary.getBeneficiaryName()
                        + " was added and is pending admin approval."
        );

        beneficiaryEmailService.sendPendingEmail(
                customerEmail,
                savedBeneficiary
        );

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "BENEFICIARY_CREATED",
                "BENEFICIARY",
                String.valueOf(savedBeneficiary.getId())
        );

        return BeneficiaryResponse.from(savedBeneficiary);
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> list(Long userId) {
        return beneficiaries.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(BeneficiaryResponse::from)
                .toList();
    }

    private String clean(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String optionalClean(String value) {
        return value == null || value.isBlank()
                ? null
                : clean(value);
    }
}