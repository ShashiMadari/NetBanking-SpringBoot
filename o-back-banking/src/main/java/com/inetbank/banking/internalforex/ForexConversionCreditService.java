package com.inetbank.banking.internalforex;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ForexConversionCreditService {

    private final ForexConversionCreditRepository repository;

    public ForexConversionCreditService(
            ForexConversionCreditRepository repository) {

        this.repository = repository;
    }

    @Transactional
    public ForexConversionCreditResponse credit(
            ForexConversionCreditRequest request) {

        ForexConversionCreditRepository.ExistingTransaction existing =
                repository.findByReference(
                        request.referenceNumber()
                ).orElse(null);

        if (existing != null) {
            boolean matches = "FOREX_CONVERSION"
                    .equals(existing.transactionType())
                    && "CREDIT".equals(existing.entrySide())
                    && "SUCCESS".equals(existing.status())
                    && existing.accountId()
                            .equals(request.destinationAccountId())
                    && existing.amount()
                            .compareTo(request.amount()) == 0;

            if (!matches) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Reference number is already in use."
                );
            }

            return new ForexConversionCreditResponse(
                    existing.transactionId(),
                    request.referenceNumber(),
                    request.amount(),
                    true,
                    "Forex conversion credit was already processed."
            );
        }

        if (!repository.creditAccount(
                request.destinationAccountId(),
                request.userId(),
                request.amount())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Destination account is unavailable or inactive."
            );
        }

        Long transactionId = repository.createCreditTransaction(
                request.destinationAccountId(),
                request.amount(),
                request.referenceNumber()
        );

        return new ForexConversionCreditResponse(
                transactionId,
                request.referenceNumber(),
                request.amount(),
                false,
                "Forex conversion credit completed."
        );
    }
}