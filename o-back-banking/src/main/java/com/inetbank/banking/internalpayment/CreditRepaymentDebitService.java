package com.inetbank.banking.internalpayment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreditRepaymentDebitService {

    private final CreditRepaymentDebitRepository repository;

    public CreditRepaymentDebitService(
            CreditRepaymentDebitRepository repository) {

        this.repository = repository;
    }

    @Transactional
    public CreditRepaymentDebitResponse debit(
            CreditRepaymentDebitRequest request) {

        CreditRepaymentDebitRepository.ExistingTransaction existing =
                repository.findByReference(
                        request.referenceNumber()
                ).orElse(null);

        if (existing != null) {
            boolean matches = "CARD_REPAYMENT"
                    .equals(existing.transactionType())
                    && "DEBIT".equals(existing.entrySide())
                    && "SUCCESS".equals(existing.status())
                    && existing.accountId()
                            .equals(request.sourceAccountId())
                    && existing.amount()
                            .compareTo(request.amount()) == 0;

            if (!matches) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Reference number is already in use."
                );
            }

            return new CreditRepaymentDebitResponse(
                    existing.transactionId(),
                    request.referenceNumber(),
                    request.amount(),
                    true,
                    "Credit-card repayment debit was already processed."
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

        return new CreditRepaymentDebitResponse(
                transactionId,
                request.referenceNumber(),
                request.amount(),
                false,
                "Credit-card repayment debit completed."
        );
    }
}