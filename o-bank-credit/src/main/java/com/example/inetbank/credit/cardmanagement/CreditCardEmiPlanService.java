package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.inetbank.credit.integration.IdentityNotificationClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;

@Service
public class CreditCardEmiPlanService {

    private static final Set<Integer> TENURES =
            Set.of(3, 6, 9, 12);

    private static final BigDecimal TWELVE =
            new BigDecimal("12");

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private final CreditCardEmiPlanRepository plans;
    private final IdentityNotificationClient notifications;
    private final IdentitySystemNotificationClient audits;
    private final BigDecimal annualInterestRate;

    public CreditCardEmiPlanService(
            CreditCardEmiPlanRepository plans,
            IdentityNotificationClient notifications,
            IdentitySystemNotificationClient audits,
            @Value("${app.credit-card-emi.annual-interest-rate:15.00}")
            BigDecimal annualInterestRate) {

        this.plans = plans;
        this.notifications = notifications;
        this.audits = audits;
        this.annualInterestRate = annualInterestRate;
    }

    @Transactional
    public CreditCardEmiPlanResponse create(
            Long userId,
            String bearerToken,
            CreateCreditCardEmiPlanRequest request) {

        if (!TENURES.contains(request.tenureMonths())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "EMI tenure must be 3, 6, 9, or 12 months."
            );
        }

        CreditCardEmiPlanRepository.EmiCardDetails card =
                plans.findActiveCreditCard(userId, request.cardId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Active credit card was not found."
                                )
                        );

        if (card.paymentDueDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Credit card payment due date is not configured."
            );
        }

        BigDecimal alreadyConverted =
                plans.activeConvertedAmount(
                        userId,
                        card.cardId()
                );

        BigDecimal convertibleAmount =
                card.outstandingAmount()
                        .subtract(alreadyConverted);

        if (request.conversionAmount()
                .compareTo(convertibleAmount) > 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Conversion amount exceeds the available card outstanding balance."
            );
        }

        LocalDate firstDueDate = card.paymentDueDate();

        if (firstDueDate.isBefore(LocalDate.now())) {
            firstDueDate = firstDueDate.plusMonths(1);
        }

        List<EmiInstallment> installments = schedule(
                request.conversionAmount(),
                annualInterestRate,
                request.tenureMonths(),
                firstDueDate
        );

        BigDecimal emiAmount = installments.get(0).emiAmount();

        BigDecimal totalPayable = installments.stream()
                .map(EmiInstallment::emiAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Long emiPlanId = plans.createPlan(
                userId,
                card.cardId(),
                request.conversionAmount(),
                annualInterestRate,
                request.tenureMonths(),
                emiAmount,
                totalPayable,
                firstDueDate
        );

        for (EmiInstallment installment : installments) {
            plans.createSchedule(
                    emiPlanId,
                    installment.installmentNumber(),
                    installment.dueDate(),
                    installment.openingPrincipal(),
                    installment.emiAmount(),
                    installment.principalComponent(),
                    installment.interestComponent(),
                    installment.closingPrincipal()
            );
        }

        CreditCardEmiPlanResponse plan = plans.findPlan(
                userId,
                emiPlanId
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Credit-card EMI plan was not created."
        ));

        notifications.createNotification(
                bearerToken,
                "CARD",
                "Credit-card EMI plan was created for INR "
                        + request.conversionAmount().toPlainString()
                        + " with "
                        + request.tenureMonths()
                        + " monthly installments."
        );

        audits.createAuditEvent(
                userId,
                "CREDIT_CARD_EMI_PLAN_CREATED",
                "CREDIT_CARD_EMI_PLAN",
                String.valueOf(emiPlanId)
        );

        return plan;
    }

    @Transactional(readOnly = true)
    public List<CreditCardEmiPlanResponse> list(Long userId) {
        return plans.findPlans(userId);
    }

    @Transactional(readOnly = true)
    public List<CreditCardEmiScheduleResponse> schedule(
            Long userId,
            Long emiPlanId) {

        plans.findPlan(userId, emiPlanId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "EMI plan was not found."
                ));

        return plans.findSchedule(emiPlanId);
    }

    private List<EmiInstallment> schedule(
            BigDecimal principal,
            BigDecimal annualRate,
            int tenureMonths,
            LocalDate firstDueDate) {

        BigDecimal monthlyRate = annualRate
                .divide(
                        ONE_HUNDRED.multiply(TWELVE),
                        12,
                        RoundingMode.HALF_UP
                );

        BigDecimal regularEmi;

        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            regularEmi = principal.divide(
                    BigDecimal.valueOf(tenureMonths),
                    2,
                    RoundingMode.HALF_UP
            );
        } else {
            BigDecimal factor = BigDecimal.ONE
                    .add(monthlyRate)
                    .pow(tenureMonths, MathContext.DECIMAL64);

            regularEmi = principal
                    .multiply(monthlyRate)
                    .multiply(factor)
                    .divide(
                            factor.subtract(BigDecimal.ONE),
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        List<EmiInstallment> result = new ArrayList<>();
        BigDecimal openingPrincipal = principal;

        for (int installment = 1;
                installment <= tenureMonths;
                installment++) {

            BigDecimal interest = openingPrincipal
                    .multiply(monthlyRate)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal emi = regularEmi;
            BigDecimal principalComponent =
                    emi.subtract(interest);

            if (installment == tenureMonths
                    || principalComponent.compareTo(
                            openingPrincipal) >= 0) {

                principalComponent = openingPrincipal;
                emi = principalComponent
                        .add(interest)
                        .setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal closingPrincipal = openingPrincipal
                    .subtract(principalComponent)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            result.add(new EmiInstallment(
                    installment,
                    firstDueDate.plusMonths(installment - 1L),
                    openingPrincipal,
                    emi,
                    principalComponent,
                    interest,
                    closingPrincipal
            ));

            openingPrincipal = closingPrincipal;
        }

        return result;
    }

    private record EmiInstallment(
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal openingPrincipal,
            BigDecimal emiAmount,
            BigDecimal principalComponent,
            BigDecimal interestComponent,
            BigDecimal closingPrincipal) {
    }
}