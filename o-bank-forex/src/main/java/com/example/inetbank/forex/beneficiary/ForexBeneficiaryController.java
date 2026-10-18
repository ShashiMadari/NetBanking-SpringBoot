package com.example.inetbank.forex.beneficiary;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/forex/beneficiaries")
@PreAuthorize("hasRole('CUSTOMER')")
public class ForexBeneficiaryController {

    private final ForexBeneficiaryService beneficiaryService;

    public ForexBeneficiaryController(
            ForexBeneficiaryService beneficiaryService) {

        this.beneficiaryService = beneficiaryService;
    }

    @PostMapping
    public ResponseEntity<ForexBeneficiaryResponse> add(
            @Valid @RequestBody ForexBeneficiaryRequest request,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(beneficiaryService.add(
                        Long.valueOf(authentication.getName()),
                        request
                ));
    }

    @GetMapping
    public List<ForexBeneficiaryResponse> list(
            Authentication authentication) {

        return beneficiaryService.list(
                Long.valueOf(authentication.getName())
        );
    }
}