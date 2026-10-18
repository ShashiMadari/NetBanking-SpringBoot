package com.example.inetbank.credit.loan;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class LoanStatementEmailService {

    private static final Logger LOG = LoggerFactory.getLogger(
            LoanStatementEmailService.class
    );

    private final ObjectProvider<JavaMailSender> mailSender;
    private final LoanStatementPdfService pdfService;
    private final String fromAddress;

    public LoanStatementEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            LoanStatementPdfService pdfService,
            @Value("${app.mail.from:no-reply@obank.demo}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.pdfService = pdfService;
        this.fromAddress = fromAddress;
    }

    public boolean send(
            String customerEmail,
            LoanMonthlyStatementResponse statement) {

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            LOG.info(
                    "SMTP is unavailable; loan statement email was not sent."
            );
            return false;
        }

        String statementMonth = statement.statementFrom()
                .getYear()
                + "-"
                + String.format(
                        "%02d",
                        statement.statementFrom().getMonthValue()
                );

        String subject = "O Bank loan statement - "
                + statementMonth;

        String attachmentName = "O-Bank-Loan-Statement-"
                + statement.loanAccountNumber()
                + "-"
                + statementMonth
                + ".pdf";

        String body = """
                Dear Customer,

                Your O Bank monthly loan statement is attached
                as a PDF document.

                Loan account: %s
                Statement period: %s to %s
                Current outstanding amount: INR %s
                Next EMI due date: %s

                Please review the attached statement carefully.

                This is an automated message. Please do not reply.

                Regards,
                O Bank
                """.formatted(
                statement.loanAccountNumber(),
                statement.statementFrom(),
                statement.statementTo(),
                statement.currentOutstandingAmount()
                        .setScale(2)
                        .toPlainString(),
                statement.nextDueDate() == null
                        ? "Not applicable"
                        : statement.nextDueDate().toString()
        );

        try {
            byte[] pdf = pdfService.create(statement);

            MimeMessage message = sender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromAddress);
            helper.setTo(customerEmail);
            helper.setSubject(subject);
            helper.setText(body, false);

            helper.addAttachment(
                    attachmentName,
                    new ByteArrayResource(pdf),
                    "application/pdf"
            );

            sender.send(message);

            LOG.info(
                    "Loan statement PDF email sent. loanId={}, month={}",
                    statement.loanId(),
                    statementMonth
            );

            return true;
        } catch (Exception exception) {
            LOG.warn(
                    "Loan statement PDF email delivery failed.",
                    exception
            );

            return false;
        }
    }
}