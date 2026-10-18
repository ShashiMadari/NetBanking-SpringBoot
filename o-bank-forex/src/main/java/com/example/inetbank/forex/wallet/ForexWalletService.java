package com.example.inetbank.forex.wallet;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForexWalletService {

    private final ForexWalletRepository walletRepository;

    public ForexWalletService(
            ForexWalletRepository walletRepository) {

        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public List<ForexWalletResponse> getWallets(
            Long userId) {

        return walletRepository.findByUserId(userId);
    }
}