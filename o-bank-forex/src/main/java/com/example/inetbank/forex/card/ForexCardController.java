package com.example.inetbank.forex.card;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forex/cards")
@PreAuthorize("hasRole('CUSTOMER')")
public class ForexCardController {

    private final ForexCardService cardService;

    public ForexCardController(
            ForexCardService cardService) {

        this.cardService = cardService;
    }

    @PostMapping("/issue")
    public ResponseEntity<ForexCardResponse> issue(
            @Valid @RequestBody ForexCardIssueRequest request,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cardService.issue(
                        Long.valueOf(authentication.getName()),
                        request
                ));
    }

    @GetMapping
    public List<ForexCardResponse> list(
            Authentication authentication) {

        return cardService.list(
                Long.valueOf(authentication.getName())
        );
    }
}