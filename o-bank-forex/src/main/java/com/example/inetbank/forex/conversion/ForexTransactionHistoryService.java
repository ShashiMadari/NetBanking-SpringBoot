package com.example.inetbank.forex.conversion;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForexTransactionHistoryService {

    private final ForexTransactionHistoryRepository repository;

    public ForexTransactionHistoryService(
            ForexTransactionHistoryRepository repository) {

        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ForexTransactionResponse> getTransactions(
            Long userId,
            String currency,
            String transactionType,
            String status,
            LocalDate from,
            LocalDate to) {

        return repository.findForUser(
                userId,
                currency,
                transactionType,
                status,
                from,
                to
        );
    }
}