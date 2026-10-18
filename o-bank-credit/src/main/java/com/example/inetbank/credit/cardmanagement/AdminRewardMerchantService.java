package com.example.inetbank.credit.cardmanagement;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;

@Service
public class AdminRewardMerchantService {

    private final AdminRewardMerchantRepository merchants;
    private final IdentitySystemNotificationClient audits;

    public AdminRewardMerchantService(
            AdminRewardMerchantRepository merchants,
            IdentitySystemNotificationClient audits) {

        this.merchants = merchants;
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public List<RewardMerchantResponse> list() {
        return merchants.findAll();
    }

    @Transactional
    public RewardMerchantResponse create(
            Long adminUserId,
            CreateRewardMerchantRequest request) {

        String merchantCode = request.merchantCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (merchants.findByCode(merchantCode).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A merchant already exists with this merchant code."
            );
        }

        merchants.create(
                merchantCode,
                clean(request.merchantName()),
                clean(request.merchantCategory())
                        .toUpperCase(Locale.ROOT),
                request.rewardEligible()
        );

        RewardMerchantResponse merchant =
                merchants.findByCode(merchantCode)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        "Merchant was created but could not be loaded."
                                )
                        );

        audits.createAuditEvent(
                adminUserId,
                "REWARD_MERCHANT_CREATED",
                "MERCHANT",
                String.valueOf(merchant.merchantId())
        );

        return merchant;
    }

    @Transactional
    public RewardMerchantResponse updateStatus(
            Long adminUserId,
            Long merchantId,
            RewardMerchantStatusRequest request) {

        RewardMerchantResponse existing = merchant(merchantId);

        String status = request.status()
                .trim()
                .toUpperCase(Locale.ROOT);

        merchants.updateStatus(merchantId, status);

        RewardMerchantResponse updated = merchant(merchantId);

        audits.createAuditEvent(
                adminUserId,
                "REWARD_MERCHANT_STATUS_CHANGED",
                "MERCHANT",
                String.valueOf(existing.merchantId())
        );

        return updated;
    }

    @Transactional
    public RewardMerchantResponse updateRewardEligibility(
            Long adminUserId,
            Long merchantId,
            RewardMerchantEligibilityRequest request) {

        RewardMerchantResponse existing = merchant(merchantId);

        merchants.updateRewardEligibility(
                merchantId,
                request.rewardEligible()
        );

        RewardMerchantResponse updated = merchant(merchantId);

        audits.createAuditEvent(
                adminUserId,
                "REWARD_MERCHANT_ELIGIBILITY_CHANGED",
                "MERCHANT",
                String.valueOf(existing.merchantId())
        );

        return updated;
    }

    private RewardMerchantResponse merchant(Long merchantId) {
        return merchants.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Merchant was not found."
                ));
    }

    private String clean(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }
}