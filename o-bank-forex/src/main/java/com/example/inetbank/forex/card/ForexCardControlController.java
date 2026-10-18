package com.example.inetbank.forex.card;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forex/cards")
@PreAuthorize("hasRole('CUSTOMER')")
public class ForexCardControlController {

    private final ForexCardControlService controlService;

    public ForexCardControlController(
            ForexCardControlService controlService) {

        this.controlService = controlService;
    }

    @PutMapping("/{cardId}/status")
    public ResponseEntity<ForexCardResponse> changeStatus(
            @PathVariable Long cardId,
            @Valid @RequestBody ForexCardStatusRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                controlService.changeStatus(
                        Long.valueOf(authentication.getName()),
                        cardId,
                        request
                )
        );
    }

    @PutMapping("/{cardId}/spend-limit")
    public ResponseEntity<ForexCardResponse> changeSpendLimit(
            @PathVariable Long cardId,
            @Valid @RequestBody ForexCardAmountRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                controlService.changeSpendLimit(
                        Long.valueOf(authentication.getName()),
                        cardId,
                        request
                )
        );
    }
}