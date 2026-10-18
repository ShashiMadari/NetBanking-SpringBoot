package com.example.inetbank.forex.compliance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComplianceFlagService {

    private final ComplianceFlagRepository repository;
    private final BigDecimal thresholdInr;

    public ComplianceFlagService(
            ComplianceFlagRepository repository,
            @Value("${app.forex.compliance-threshold-inr:250000}")
            BigDecimal thresholdInr) {

        this.repository = repository;
        this.thresholdInr = thresholdInr;
    }

    public void flagHighValueTransaction(
            Long forexTransactionId,
            BigDecimal inrEquivalent) {

        if (inrEquivalent.compareTo(thresholdInr) < 0) {
            return;
        }

        repository.createHighValueFlag(
                forexTransactionId,
                "Forex transaction INR equivalent of INR "
                        + inrEquivalent
                        + " meets or exceeds the compliance "
                        + "threshold of INR "
                        + thresholdInr
                        + "."
        );
    }

    public List<ComplianceFlagResponse> findAll(
            String status) {

        String normalized = status == null
                ? null
                : status.trim().toUpperCase(Locale.ROOT);

        if (normalized != null
                && !normalized.isBlank()
                && !"OPEN".equals(normalized)
                && !"REVIEWED".equals(normalized)
                && !"CLEARED".equals(normalized)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "status must be OPEN, REVIEWED, or CLEARED."
            );
        }

        return repository.findAll(normalized);
    }

    public ComplianceFlagResponse review(
            Long adminUserId,
            Long complianceFlagId,
            ComplianceReviewRequest request) {

        String status = request.status()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!repository.review(
                complianceFlagId,
                adminUserId,
                status)) {

            ComplianceFlagResponse flag = repository.findById(
                    complianceFlagId
            ).orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Compliance flag was not found."
                    )
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Compliance flag is already "
                            + flag.status()
                            + "."
            );
        }

        return repository.findById(complianceFlagId)
                .orElseThrow();
    }
}