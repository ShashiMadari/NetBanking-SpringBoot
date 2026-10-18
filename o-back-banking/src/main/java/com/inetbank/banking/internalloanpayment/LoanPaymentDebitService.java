package com.inetbank.banking.internalloanpayment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoanPaymentDebitService {

    private final LoanPaymentDebitRepository repository;

    public LoanPaymentDebitService(
            LoanPaymentDebitRepository repository) {

        this.repository = repository;
    }

    @Transactional
    public LoanPaymentDebitResponse debit(
            LoanPaymentDebitRequest request) {

        String transactionType = transactionType(
                request.paymentType()
        );

        LoanPaymentDebitRepository.ExistingTransaction existing =
                repository.findByReference(
                        request.referenceNumber()
                ).orElse(null);

        if (existing != null) {
            boolean matches = transactionType.equals(
                    existing.transactionType()
            )
                    && "DEBIT".equals(existing.entrySide())
                    && "SUCCESS".equals(existing.status())
                    && existing.accountId().equals(
                            request.sourceAccountId()
                    )
                    && existing.amount().compareTo(
                            request.amount()
                    ) == 0;

            if (!matches) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Reference number is already in use."
                );
            }

            return new LoanPaymentDebitResponse(
                    existing.transactionId(),
                    request.referenceNumber(),
                    request.amount(),
                    true,
                    "Loan payment debit was already processed."
            );
        }

        if (!repository.debitAccount(
                request.sourceAccountId(),
                request.userId(),
                request.amount())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Source account is unavailable, inactive, "
                            + "or has insufficient balance."
            );
        }

        Long transactionId = repository.createDebitTransaction(
                request.sourceAccountId(),
                transactionType,
                request.amount(),
                request.referenceNumber()
        );

        return new LoanPaymentDebitResponse(
                transactionId,
                request.referenceNumber(),
                request.amount(),
                false,
                "Loan payment debit completed."
        );
    }

    private String transactionType(String paymentType) {
        return switch (paymentType) {
            case "EMI" -> "LOAN_EMI";
            case "PART_PAYMENT" -> "LOAN_PART_PAYMENT";
            case "FORECLOSURE" -> "LOAN_FORECLOSURE";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid loan payment type."
            );
        };
    }
}