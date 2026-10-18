package com.inetbank.banking.investmentpayment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InvestmentPurchaseDebitService {

    private final InvestmentPurchaseDebitRepository repository;

    public InvestmentPurchaseDebitService(
            InvestmentPurchaseDebitRepository repository) {

        this.repository = repository;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public InvestmentPurchaseDebitResponse debit(
            InvestmentPurchaseDebitRequest request) {

        InvestmentPurchaseDebitRepository.ExistingTransaction existing =
                repository.findByReference(
                        request.referenceNumber())
                .orElse(null);

        if (existing != null) {
            boolean matches = "INVESTMENT_PURCHASE"
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
                        "Reference number is already in use.");
            }

            return new InvestmentPurchaseDebitResponse(
                    existing.transactionId(),
                    request.referenceNumber(),
                    request.amount(),
                    true,
                    "Investment purchase debit was already processed.");
        }

        if (!repository.debitAccount(
                request.sourceAccountId(),
                request.userId(),
                request.amount())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Source account is unavailable, inactive, "
                            + "or has insufficient balance.");
        }

        Long transactionId = repository.createDebitTransaction(
                request.sourceAccountId(),
                request.amount(),
                request.referenceNumber());

        return new InvestmentPurchaseDebitResponse(
                transactionId,
                request.referenceNumber(),
                request.amount(),
                false,
                "Investment purchase debit completed.");
    }
}