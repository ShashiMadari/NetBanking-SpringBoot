package com.example.inetbank.credit.cardmanagement;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/credit-card-emi")
@PreAuthorize("hasRole('CUSTOMER')")
public class CreditCardEmiPlanController {

    private final CreditCardEmiPlanService emiPlanService;

    public CreditCardEmiPlanController(
            CreditCardEmiPlanService emiPlanService) {

        this.emiPlanService = emiPlanService;
    }

    @PostMapping("/plans")
    public ResponseEntity<CreditCardEmiPlanResponse> createPlan(
            @Valid @RequestBody CreateCreditCardEmiPlanRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(emiPlanService.create(
                        userId(authentication),
                        bearerToken,
                        request
                ));
    }

    @GetMapping("/plans")
    public List<CreditCardEmiPlanResponse> plans(
            Authentication authentication) {

        return emiPlanService.list(
                userId(authentication)
        );
    }

    @GetMapping("/plans/{emiPlanId}/schedule")
    public List<CreditCardEmiScheduleResponse> schedule(
            @PathVariable Long emiPlanId,
            Authentication authentication) {

        return emiPlanService.schedule(
                userId(authentication),
                emiPlanId
        );
    }

    private Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}