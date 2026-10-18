package com.inetbank.banking.account;

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
public class MonthlyAccountStatementEmailService {

    private static final Logger LOG = LoggerFactory.getLogger(
            MonthlyAccountStatementEmailService.class
    );

    private final ObjectProvider<JavaMailSender> mailSender;
    private final MonthlyAccountStatementPdfService pdfService;
    private final String fromAddress;

    public MonthlyAccountStatementEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            MonthlyAccountStatementPdfService pdfService,
            @Value("${app.mail.from:no-reply@obank.demo}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.pdfService = pdfService;
        this.fromAddress = fromAddress;
    }

    public boolean send(
            String customerEmail,
            MonthlyAccountStatementData statement) {

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            LOG.info(
                    "SMTP is unavailable; account-statement email was not sent."
            );
            return false;
        }

        String month = statement.statementMonth().toString();

        String attachmentName = "O-Bank-Account-Statement-"
                + statement.accountNumber()
                + "-"
                + month
                + ".pdf";

        String body = """
                Dear %s,

                Your O Bank monthly account statement is attached
                as a PDF document.

                Account number: %s
                Statement month: %s
                Available balance: INR %s

                Please review the attached statement carefully.

                This is an automated message. Please do not reply.

                Regards,
                O Bank
                """.formatted(
                statement.customerName(),
                statement.accountNumber(),
                month,
                statement.availableBalance()
                        .setScale(2)
                        .toPlainString()
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
            helper.setSubject(
                    "O Bank monthly account statement - " + month
            );
            helper.setText(body, false);

            helper.addAttachment(
                    attachmentName,
                    new ByteArrayResource(pdf),
                    "application/pdf"
            );

            sender.send(message);

            LOG.info(
                    "Account-statement PDF email sent. accountId={}, month={}",
                    statement.accountId(),
                    month
            );

            return true;
        } catch (Exception exception) {
            LOG.warn(
                    "Account-statement PDF email delivery failed.",
                    exception
            );

            return false;
        }
    }
}