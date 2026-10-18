package com.inetbank.banking.internalpayment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreditCardEmiDebitService {

    private final CreditCardEmiDebitRepository debits;

    public CreditCardEmiDebitService(
            CreditCardEmiDebitRepository debits) {

        this.debits = debits;
    }

    @Transactional
    public CreditCardEmiDebitResponse debit(
            CreditCardEmiDebitRequest request) {

        CreditCardEmiDebitRepository.ExistingTransaction existing =
                debits.findByReference(request.referenceNumber())
                        .orElse(null);

        if (existing != null) {
            validateExisting(existing, request);

            return new CreditCardEmiDebitResponse(
                    existing.transactionId(),
                    request.referenceNumber(),
                    existing.amount(),
                    true,
                    "Credit-card EMI debit was already processed."
            );
        }

        if (!debits.debitAccount(
                request.userId(),
                request.sourceAccountId(),
                request.amount())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Source account is inactive or has insufficient balance."
            );
        }

        Long transactionId = debits.createDebitTransaction(
                request.sourceAccountId(),
                request.amount(),
                request.referenceNumber()
        );

        return new CreditCardEmiDebitResponse(
                transactionId,
                request.referenceNumber(),
                request.amount(),
                false,
                "Credit-card EMI amount debited successfully."
        );
    }

    private void validateExisting(
            CreditCardEmiDebitRepository.ExistingTransaction existing,
            CreditCardEmiDebitRequest request) {

        boolean sameTransaction =
                "CARD_EMI_PAYMENT".equals(
                        existing.transactionType())
                && "DEBIT".equals(existing.entrySide())
                && "SUCCESS".equals(existing.status())
                && existing.accountId().equals(
                        request.sourceAccountId())
                && existing.amount().compareTo(
                        request.amount()) == 0;

        if (!sameTransaction) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This reference number belongs to a different transaction."
            );
        }
    }
}