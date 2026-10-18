package com.inetbank.investment.statement;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class InvestmentMonthlyStatementEmailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final InvestmentMonthlyStatementPdfService pdfService;
    private final String fromAddress;

    public InvestmentMonthlyStatementEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            InvestmentMonthlyStatementPdfService pdfService,
            @Value("${app.mail.from:no-reply@obank.demo}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.pdfService = pdfService;
        this.fromAddress = fromAddress;
    }

    public void send(
            String customerEmail,
            Map<String, Object> statement) {

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            throw new IllegalStateException(
                    "Email delivery is not configured."
            );
        }

        String statementMonth = String.valueOf(
                statement.get("statementMonth")
        );

        String attachmentName =
                "O-Bank-Investment-Statement-"
                        + statementMonth
                        + ".pdf";

        int transactionCount = transactionCount(statement);

        String subject = "O Bank investment statement - "
                + statementMonth;

        String body = """
                Dear Customer,

                Your O Bank monthly investment statement is attached
                as a PDF document.

                Statement period: %s to %s
                Investment activity entries: %d
                Current portfolio value: INR %s

                Please review the attached statement carefully.

                This is an automated message. Please do not reply.

                Regards,
                O Bank
                """.formatted(
                statement.get("statementFrom"),
                statement.get("statementTo"),
                transactionCount,
                statement.get("currentPortfolioValue")
        );

        try {
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
                    new ByteArrayResource(
                            pdfService.create(statement)
                    ),
                    "application/pdf"
            );

            sender.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Investment statement email could not be sent.",
                    exception
            );
        }
    }

    private int transactionCount(
            Map<String, Object> statement) {

        Object entries = statement.get("entries");

        return entries instanceof List<?> list
                ? list.size()
                : 0;
    }
}