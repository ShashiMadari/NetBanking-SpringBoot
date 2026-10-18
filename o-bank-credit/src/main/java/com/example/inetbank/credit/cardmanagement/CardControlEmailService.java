package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.internet.MimeMessage;

@Service
public class CardControlEmailService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    CardControlEmailService.class
            );

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;

    public CardControlEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${mail.from:no-reply@obank.demo}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendStatusChange(
            String customerEmail,
            CreditCardRepository.CardControlDetails card,
            String action) {

        String subject = "O Bank card " + action;

        String body = """
                <html>
                <body style="font-family: Arial, sans-serif; color: #1f2937;">
                  <h2 style="color: #0f4c81;">O Bank Card Security Update</h2>
                  <p>Your %s card ending in <strong>%s</strong> has been
                  <strong>%s</strong>.</p>
                  <p>If you did not request this action, contact O Bank support
                  immediately.</p>
                  <br>
                  <p>Regards,<br><strong>O Bank</strong></p>
                </body>
                </html>
                """.formatted(
                HtmlUtils.htmlEscape(card.cardType().toLowerCase()),
                HtmlUtils.htmlEscape(card.lastFour()),
                HtmlUtils.htmlEscape(action)
        );

        send(customerEmail, subject, body);
    }

    public void sendDailyLimitChange(
            String customerEmail,
            CreditCardRepository.CardControlDetails card,
            BigDecimal dailyLimit) {

        String subject = "O Bank card daily limit updated";

        String body = """
                <html>
                <body style="font-family: Arial, sans-serif; color: #1f2937;">
                  <h2 style="color: #0f4c81;">O Bank Card Limit Update</h2>
                  <p>The daily spending limit for your %s card ending in
                  <strong>%s</strong> has been updated.</p>
                  <p><strong>New daily limit:</strong> INR %s</p>
                  <p>If you did not request this change, contact O Bank support
                  immediately.</p>
                  <br>
                  <p>Regards,<br><strong>O Bank</strong></p>
                </body>
                </html>
                """.formatted(
                HtmlUtils.htmlEscape(card.cardType().toLowerCase()),
                HtmlUtils.htmlEscape(card.lastFour()),
                HtmlUtils.htmlEscape(
                        dailyLimit.stripTrailingZeros()
                                .toPlainString()
                )
        );

        send(customerEmail, subject, body);
    }

    private void send(
            String recipient,
            String subject,
            String htmlBody) {

        if (recipient == null || recipient.isBlank()) {
            LOG.warn("Card email was skipped because customer email is missing.");
            return;
        }

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            LOG.info("SMTP is unavailable; card email was not sent.");
            return;
        }

        try {
            MimeMessage message = sender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            sender.send(message);

        } catch (Exception exception) {
            LOG.warn(
                    "Card-control email delivery failed.",
                    exception
            );
        }
    }
}