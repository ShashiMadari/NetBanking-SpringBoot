package com.example.inetbank.credit.cardmanagement;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credit-cards")
@PreAuthorize("hasRole('CUSTOMER')")
public class CreditCardActivityController {

    private final CreditCardActivityService activityService;

    public CreditCardActivityController(
            CreditCardActivityService activityService) {

        this.activityService = activityService;
    }

    @GetMapping("/{cardId}/activity")
    public CreditCardActivityPageResponse activity(
            @PathVariable Long cardId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        return activityService.activity(
                Long.valueOf(authentication.getName()),
                cardId,
                page,
                size
        );
    }
}