package com.example.inetbank.forex.dashboard;

import java.util.List;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;
import com.example.inetbank.forex.wallet.ForexWalletResponse;

public record ForexDashboardResponse(

        List<ForexWalletResponse> wallets,

        List<ForexTransactionResponse> recentTransactions
) {
}