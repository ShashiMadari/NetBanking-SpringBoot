package com.example.inetbank.forex.card;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;

@RestController
@RequestMapping("/api/forex/cards")
@PreAuthorize("hasRole('CUSTOMER')")
public class ForexCardSpendController {

    private final ForexCardSpendService cardSpendService;

    public ForexCardSpendController(
            ForexCardSpendService cardSpendService) {

        this.cardSpendService = cardSpendService;
    }

    @PostMapping("/{cardId}/simulate-spend")
    public ResponseEntity<ForexTransactionResponse> spend(
            @PathVariable Long cardId,
            @Valid @RequestBody ForexCardSpendRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                cardSpendService.spend(
                        Long.valueOf(authentication.getName()),
                        cardId,
                        request
                )
        );
    }
}