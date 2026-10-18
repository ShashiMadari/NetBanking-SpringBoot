package com.example.inetbank.credit.cardmanagement;

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
public class CreditCardMonthlyStatementEmailService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    CreditCardMonthlyStatementEmailService.class
            );

    private final ObjectProvider<JavaMailSender> mailSender;
    private final CreditCardMonthlyStatementPdfService pdfService;
    private final String fromAddress;

    public CreditCardMonthlyStatementEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            CreditCardMonthlyStatementPdfService pdfService,
            @Value("${app.mail.from:no-reply@obank.demo}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.pdfService = pdfService;
        this.fromAddress = fromAddress;
    }

    public boolean send(
            String customerEmail,
            CreditCardMonthlyStatementResponse statement) {

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            LOG.info(
                    "SMTP is unavailable; credit-card statement email was not sent."
            );
            return false;
        }

        String statementMonth = String.format(
                "%04d-%02d",
                statement.year(),
                statement.month()
        );

        String subject = "O Bank credit-card statement - "
                + statementMonth;

        String attachmentName =
                "O-Bank-Credit-Card-Statement-"
                        + statementMonth
                        + ".pdf";

        String body = """
                Dear Customer,

                Your O Bank credit-card monthly statement is attached
                as a PDF document.

                Card: XXXX%s
                Statement period: %s to %s

                Please review the attached statement carefully.

                This is an automated message. Please do not reply.

                Regards,
                O Bank
                """.formatted(
                statement.cardLastFour(),
                statement.statementFrom(),
                statement.statementTo()
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
                    "Credit-card statement PDF email sent. cardId={}, month={}",
                    statement.cardId(),
                    statementMonth
            );

            return true;
        } catch (Exception exception) {
            LOG.warn(
                    "Credit-card statement PDF email delivery failed.",
                    exception
            );

            return false;
        }
    }
}