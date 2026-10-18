package com.inetbank.banking.internalforex;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ForexConversionDebitService {

    private final ForexConversionDebitRepository repository;

    public ForexConversionDebitService(
            ForexConversionDebitRepository repository) {

        this.repository = repository;
    }

    @Transactional
    public ForexConversionDebitResponse debit(
            ForexConversionDebitRequest request) {

        ForexConversionDebitRepository.ExistingTransaction existing =
                repository.findByReference(
                        request.referenceNumber()
                ).orElse(null);

        if (existing != null) {
            boolean matches = "FOREX_CONVERSION".equals(
                    existing.transactionType()
            ) && "DEBIT".equals(
                    existing.entrySide()
            ) && "SUCCESS".equals(
                    existing.status()
            ) && existing.accountId().equals(
                    request.sourceAccountId()
            ) && existing.amount().compareTo(
                    request.amount()
            ) == 0;

            if (!matches) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Reference number is already in use."
                );
            }

            return new ForexConversionDebitResponse(
                    existing.transactionId(),
                    request.referenceNumber(),
                    request.amount(),
                    true,
                    "Forex conversion debit was already processed."
            );
        }

        if (!repository.debitAccount(
                request.sourceAccountId(),
                request.userId(),
                request.amount())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Source account is unavailable, inactive, or has insufficient balance."
            );
        }

        Long transactionId = repository.createDebitTransaction(
                request.sourceAccountId(),
                request.amount(),
                request.referenceNumber()
        );

        return new ForexConversionDebitResponse(
                transactionId,
                request.referenceNumber(),
                request.amount(),
                false,
                "Forex conversion debit completed."
        );
    }
}