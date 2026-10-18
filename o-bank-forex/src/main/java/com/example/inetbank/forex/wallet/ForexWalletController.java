package com.example.inetbank.forex.wallet;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forex")
@PreAuthorize("hasRole('CUSTOMER')")
public class ForexWalletController {

    private final ForexWalletService walletService;

    public ForexWalletController(
            ForexWalletService walletService) {

        this.walletService = walletService;
    }

    @GetMapping("/wallets")
    public List<ForexWalletResponse> getWallets(
            Authentication authentication) {

        return walletService.getWallets(
                Long.valueOf(authentication.getName())
        );
    }
}