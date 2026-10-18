package com.inetbank.banking.beneficiary;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/beneficiaries")
@PreAuthorize("hasRole('CUSTOMER')")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(
            BeneficiaryService beneficiaryService) {

        this.beneficiaryService = beneficiaryService;
    }

    @PostMapping
    public ResponseEntity<BeneficiaryResponse> add(
            @Valid @RequestBody AddBeneficiaryRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());
        String customerEmail = (String) authentication.getDetails();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(beneficiaryService.add(
                        userId,
                        customerEmail,
                        bearerToken,
                        request
                ));
    }

    @GetMapping
    public List<BeneficiaryResponse> list(
            Authentication authentication) {

        return beneficiaryService.list(
                Long.valueOf(authentication.getName())
        );
    }
}