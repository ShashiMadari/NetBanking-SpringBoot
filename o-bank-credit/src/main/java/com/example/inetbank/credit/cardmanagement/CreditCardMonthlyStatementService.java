package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreditCardMonthlyStatementService {

    private final CreditCardMonthlyStatementRepository statements;
    private final CreditCardMonthlyStatementEmailService emailService;

    public CreditCardMonthlyStatementService(
            CreditCardMonthlyStatementRepository statements,
            CreditCardMonthlyStatementEmailService emailService) {

        this.statements = statements;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public CreditCardMonthlyStatementResponse getStatement(
            Long userId,
            Long cardId,
            int year,
            int month) {

        YearMonth statementMonth = statementMonth(year, month);

        CreditCardMonthlyStatementRepository.CardStatementDetails card =
                cardForUser(cardId, userId);

        LocalDate fromDate = statementMonth.atDay(1);
        LocalDate toDateExclusive = statementMonth.plusMonths(1)
                .atDay(1);

        List<CreditCardMonthlyStatementEntryResponse> entries =
                statements.findEntries(
                        cardId,
                        fromDate,
                        toDateExclusive
                );

        return new CreditCardMonthlyStatementResponse(
                card.cardId(),
                card.lastFour(),
                year,
                month,
                fromDate,
                toDateExclusive.minusDays(1),
                card.outstandingAmount(),
                card.minimumDueAmount(),
                card.paymentDueDate(),
                total(entries, "MERCHANT_PURCHASE"),
                total(entries, "CARD_REPAYMENT"),
                total(entries, "BILL_PAYMENT"),
                total(entries, "EMI_CONVERSION"),
                total(entries, "EMI_PAYMENT"),
                reversedTotal(entries),
                entries
        );
    }

    @Transactional(readOnly = true)
    public void emailStatement(
            Long userId,
            String customerEmail,
            Long cardId,
            int year,
            int month) {

        CreditCardMonthlyStatementResponse statement = getStatement(
                userId,
                cardId,
                year,
                month
        );

        emailService.send(customerEmail, statement);
    }

    private YearMonth statementMonth(
            int year,
            int month) {

        YearMonth result;

        try {
            result = YearMonth.of(year, month);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide a valid statement year and month."
            );
        }

        if (result.isAfter(YearMonth.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A statement cannot be generated for a future month."
            );
        }

        return result;
    }

    private CreditCardMonthlyStatementRepository.CardStatementDetails
            cardForUser(
                    Long cardId,
                    Long userId) {

        return statements.findCardForUser(cardId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Credit card was not found."
                ));
    }

    private BigDecimal total(
            List<CreditCardMonthlyStatementEntryResponse> entries,
            String activityType) {

        return entries.stream()
                .filter(entry -> activityType.equals(
                        entry.activityType()
                ))
                .map(CreditCardMonthlyStatementEntryResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal reversedTotal(
            List<CreditCardMonthlyStatementEntryResponse> entries) {

        return entries.stream()
                .filter(entry -> "REVERSED".equals(entry.status()))
                .map(CreditCardMonthlyStatementEntryResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}