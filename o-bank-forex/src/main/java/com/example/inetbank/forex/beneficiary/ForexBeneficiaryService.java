package com.example.inetbank.forex.beneficiary;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForexBeneficiaryService {

    private final ForexBeneficiaryRepository repository;

    public ForexBeneficiaryService(
            ForexBeneficiaryRepository repository) {

        this.repository = repository;
    }

    @Transactional
    public ForexBeneficiaryResponse add(
            Long userId,
            ForexBeneficiaryRequest request) {

        String countryCode = request.countryCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        String currencyCode = request.currencyCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        String swiftBic = request.swiftBic()
                .trim()
                .toUpperCase(Locale.ROOT);

        Long beneficiaryId = repository.create(
                userId,
                request,
                countryCode,
                currencyCode,
                swiftBic
        );

        return repository.findByIdAndUserId(
                beneficiaryId,
                userId
        );
    }

    @Transactional(readOnly = true)
    public List<ForexBeneficiaryResponse> list(
            Long userId) {

        return repository.findByUserId(userId);
    }
}