package com.example.inetbank.forex.statement;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class ForexStatementEmailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ForexStatementPdfService pdfService;
    private final String fromAddress;

    public ForexStatementEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            ForexStatementPdfService pdfService,
            @Value("${app.mail.from:no-reply@obank.demo}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.pdfService = pdfService;
        this.fromAddress = fromAddress;
    }

    public void send(
            String customerEmail,
            ForexTransactionStatementResponse statement) {

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            throw new IllegalStateException(
                    "Email delivery is not configured."
            );
        }

        String filename = "O-Bank-Forex-Statement-"
                + statement.statementFrom()
                + "-to-"
                + statement.statementTo()
                + ".pdf";

        String subject = "O Bank Forex statement - "
                + statement.statementFrom()
                + " to "
                + statement.statementTo();

        String body = """
                Dear Customer,

                Your O Bank Forex transaction statement is attached
                as a PDF document.

                Statement period: %s to %s
                Transactions included: %d

                Please review the attached statement carefully.

                This is an automated message. Please do not reply.

                Regards,
                O Bank
                """.formatted(
                statement.statementFrom(),
                statement.statementTo(),
                statement.totalTransactions()
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
                    filename,
                    new ByteArrayResource(pdf),
                    MediaTypes.PDF
            );

            sender.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Forex statement email could not be sent.",
                    exception
            );
        }
    }

    private static final class MediaTypes {
        private static final String PDF = "application/pdf";

        private MediaTypes() {
        }
    }
}