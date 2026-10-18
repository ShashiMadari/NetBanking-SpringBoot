package com.inetbank.identity.notification;

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
public class NotificationEmailService {

    private static final Logger LOG =
            LoggerFactory.getLogger(NotificationEmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;

    public NotificationEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from:no-reply@obank.demo}") String fromAddress) {

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendAdminNotificationEmail(
            String customerEmail,
            String eventType,
            String message) {

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            LOG.info("SMTP is unavailable; notification email was not sent.");
            return;
        }

        try {
            String title = emailTitle(eventType, message);

            MimeMessage email = sender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    email,
                    false,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromAddress);
            helper.setTo(customerEmail);
            helper.setSubject("O Bank | " + title);
            helper.setText(emailHtml(title, message), true);

            sender.send(email);

        } catch (RuntimeException | jakarta.mail.MessagingException exception) {
            LOG.warn(
                    "Notification email delivery failed for customer {}.",
                    customerEmail,
                    exception
            );
        }
    }

    private String emailTitle(String eventType, String message) {
        String text = message == null ? "" : message.toLowerCase();

        if ("PAYMENT".equals(eventType) && text.contains("reversed")) {
            return "Payment Reversal Confirmation";
        }

        if ("PAYMENT".equals(eventType)) {
            return "Payment Update";
        }

        if ("BENEFICIARY".equals(eventType)) {
            return "Beneficiary Update";
        }

        if ("SECURITY".equals(eventType)) {
            return "Security Alert";
        }

        return "Account Notification";
    }

    private String emailHtml(String title, String message) {
        String safeTitle = HtmlUtils.htmlEscape(title);
        String safeMessage = HtmlUtils.htmlEscape(message)
                .replace("\n", "<br>");

        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0; padding:0; background:#f4f7fb;
                            font-family:Arial, sans-serif; color:#1f2937;">
                <div style="max-width:620px; margin:32px auto;
                            background:#ffffff; border-radius:12px;
                            overflow:hidden; box-shadow:0 2px 10px rgba(0,0,0,.08);">

                    <div style="background:#0b3d91; padding:26px 32px;">
                    <div style="color:#ffffff; font-size:24px; font-weight:700;">
                        O Bank
                    </div>
                    <div style="color:#dbeafe; font-size:14px; margin-top:5px;">
                        Secure Digital Banking
                    </div>
                    </div>

                    <div style="padding:32px;">
                    <h2 style="margin:0 0 20px; color:#0b3d91; font-size:21px;">
                        %s
                    </h2>

                    <p style="font-size:16px; line-height:1.65;">
                        Dear Customer,
                    </p>

                    <p style="font-size:16px; line-height:1.65;">
                        %s
                    </p>

                    <div style="margin-top:28px; padding:16px;
                                background:#eff6ff; border-left:4px solid #0b3d91;
                                font-size:14px; line-height:1.55;">
                        If you did not expect this activity, please contact
                        O Bank Customer Support immediately.
                    </div>

                    <p style="margin-top:28px; font-size:16px; line-height:1.6;">
                        Regards,<br>
                        <strong>O Bank Customer Support</strong>
                    </p>
                    </div>

                    <div style="padding:18px 32px; background:#f8fafc;
                                color:#64748b; font-size:12px; line-height:1.5;">
                    This is an automated notification. Please do not reply
                    directly to this email.
                    </div>
                </div>
                </body>
                </html>
                """.formatted(safeTitle, safeMessage);
    }
}