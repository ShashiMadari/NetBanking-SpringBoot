package com.example.inetbank.credit.cardmanagement;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/credit-card-rewards")
@PreAuthorize("hasRole('CUSTOMER')")
public class CreditCardRewardController {

    private final CreditCardRewardService rewardService;

    public CreditCardRewardController(
            CreditCardRewardService rewardService) {

        this.rewardService = rewardService;
    }

    @GetMapping("/merchants")
    public List<RewardMerchantResponse> merchants() {
        return rewardService.merchants();
    }

    @GetMapping("/balance")
    public CreditCardRewardBalanceResponse balance(
            @RequestParam Long cardId,
            Authentication authentication) {

        return rewardService.balance(
                userId(authentication),
                cardId
        );
    }

    @GetMapping("/ledger")
    public CreditCardRewardLedgerPageResponse ledger(
            @RequestParam Long cardId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        return rewardService.ledger(
                userId(authentication),
                cardId,
                page,
                size
        );
    }

    @PostMapping("/purchases")
    public MerchantPurchaseResponse purchase(
            @Valid @RequestBody MerchantPurchaseRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            Authentication authentication) {

        return rewardService.purchase(
                userId(authentication),
                customerEmail(authentication),
                bearerToken,
                request
        );
    }

    private Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    private String customerEmail(Authentication authentication) {
        Object details = authentication.getDetails();

        return details instanceof String email ? email : null;
    }

    @GetMapping("/purchases")
    public MerchantPurchaseHistoryPageResponse purchases(
            @RequestParam Long cardId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        return rewardService.purchases(
                userId(authentication),
                cardId,
                page,
                size
        );
    }
}