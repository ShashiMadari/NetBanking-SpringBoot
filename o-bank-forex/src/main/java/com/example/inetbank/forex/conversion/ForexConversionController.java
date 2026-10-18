package com.example.inetbank.forex.conversion;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/forex")
@PreAuthorize("hasRole('CUSTOMER')")
public class ForexConversionController {

    private final ForexConversionService conversionService;

    public ForexConversionController(
            ForexConversionService conversionService) {

        this.conversionService = conversionService;
    }

    @PostMapping("/convert")
    public ResponseEntity<ForexTransactionResponse> convert(
            @Valid @RequestBody ForexConversionRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                conversionService.convert(
                        Long.valueOf(authentication.getName()),
                        request
                )
        );
    }
}