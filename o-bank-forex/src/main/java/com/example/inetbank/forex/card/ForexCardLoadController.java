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
public class ForexCardLoadController {

    private final ForexCardLoadService cardLoadService;

    public ForexCardLoadController(
            ForexCardLoadService cardLoadService) {

        this.cardLoadService = cardLoadService;
    }

    @PostMapping("/{cardId}/load")
    public ResponseEntity<ForexTransactionResponse> load(
            @PathVariable Long cardId,
            @Valid @RequestBody ForexCardAmountRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                cardLoadService.load(
                        Long.valueOf(authentication.getName()),
                        cardId,
                        request
                )
        );
    }
}