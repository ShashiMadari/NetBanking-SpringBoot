package com.inetbank.identity.registration;

import java.time.Year;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger LOG =
            LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;

    EmailService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from:no-reply@obank.demo}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendDemoPassword(
            String email,
            String name,
            String demoPassword) {

        String safeName = escape(name);

        String content = """
                <p>Hello %s,</p>
                <p>Your O-Bank registration request has been received.</p>
                <p>Use the temporary password below once on the
                <strong>Set Password</strong> screen.</p>
                <div class="otp-box">%s</div>
                <p class="security-note">
                    For your security, do not share this password with anyone.
                </p>
                """.formatted(
                safeName,
                escape(demoPassword)
        );

        sendHtml(
                email,
                "Complete your O-Bank registration",
                "Complete your registration",
                content
        );
    }

    public void sendOtp(
            String email,
            String otp,
            String purpose) {

        String safePurpose = escape(capitalise(purpose));

        String content = """
                <p>Your O-Bank verification code for
                <strong>%s</strong> is:</p>
                <div class="otp-box">%s</div>
                <p>This code expires in <strong>5 minutes</strong>.</p>
                <p class="security-note">
                    O-Bank will never ask you to share this code by phone,
                    email, or message.
                </p>
                """.formatted(
                safePurpose,
                escape(otp)
        );

        sendHtml(
                email,
                "O-Bank " + purpose + " verification code",
                safePurpose + " verification",
                content
        );
    }

    public void sendAccountStatusChange(
            String customerEmail,
            String customerName,
            String accountNumber,
            String accountStatus,
            String reason) {

        boolean frozen = "FROZEN".equalsIgnoreCase(accountStatus);
        String action = frozen ? "frozen" : "unfrozen";
        String heading = frozen
                ? "Your account has been frozen"
                : "Your account is active again";

        String content = """
                <p>Hello %s,</p>
                <p>%s.</p>

                <div class="detail-card">
                    <div class="detail-row">
                        <span>Account</span>
                        <strong>%s</strong>
                    </div>
                    <div class="detail-row">
                        <span>Current status</span>
                        <strong class="%s">%s</strong>
                    </div>
                    <div class="detail-row">
                        <span>Reason</span>
                        <strong>%s</strong>
                    </div>
                </div>

                <p>
                    If you need assistance, please contact O-Bank support.
                </p>
                """.formatted(
                escape(customerName),
                heading,
                escape(maskAccountNumber(accountNumber)),
                frozen ? "status-danger" : "status-success",
                frozen ? "FROZEN" : "ACTIVE",
                escape(reason)
        );

        sendHtml(
                customerEmail,
                "O-Bank account " + action,
                heading,
                content
        );
    }

    private void sendHtml(
            String recipient,
            String subject,
            String heading,
            String content) {

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            LOG.info(
                    "SMTP is not configured; email delivery was skipped. subject={}",
                    subject
            );
            return;
        }

        try {
            MimeMessage message = sender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    "UTF-8"
            );

            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(buildEmailHtml(heading, content), true);

            sender.send(message);

        } catch (Exception exception) {
            LOG.warn(
                    "SMTP delivery failed. subject={}, recipient={}",
                    subject,
                    recipient,
                    exception
            );
        }
    }

    private String buildEmailHtml(
            String heading,
            String content) {

        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                </head>
                <body style="margin:0;padding:0;background:#f6f3f7;
                             font-family:Arial,Helvetica,sans-serif;color:#30203a;">
                  <div style="max-width:620px;margin:32px auto;background:#ffffff;
                              border:1px solid #eaddea;border-radius:18px;
                              overflow:hidden;box-shadow:0 8px 30px rgba(61,31,70,.08);">

                    <div style="background:#63346b;padding:28px 36px;">
                      <div style="color:#ffffff;font-size:25px;font-weight:700;">
                        O-Bank
                      </div>
                      <div style="color:#f3e7f5;font-size:13px;letter-spacing:1px;
                                  margin-top:6px;">
                        SECURE DIGITAL BANKING
                      </div>
                    </div>

                    <div style="padding:36px;">
                      <h1 style="margin:0 0 22px;font-size:25px;color:#3b2444;">
                        %s
                      </h1>

                      <div style="font-size:16px;line-height:1.65;color:#4c3b52;">
                        %s
                      </div>
                    </div>

                    <div style="padding:20px 36px;background:#fbf8fb;
                                border-top:1px solid #eee3ee;
                                font-size:12px;line-height:1.55;color:#78687e;">
                      This is an automated O-Bank message. Please do not reply
                      to this email.<br>
                      © %d O-Bank. Secure banking made simple.
                    </div>
                  </div>

                  <style>
                    .otp-box {
                      margin:22px 0;
                      padding:16px;
                      text-align:center;
                      font-size:28px;
                      font-weight:700;
                      letter-spacing:8px;
                      color:#63346b;
                      background:#f7edf8;
                      border:1px dashed #a375aa;
                      border-radius:12px;
                    }
                    .security-note {
                      padding:13px 15px;
                      background:#fff8e8;
                      border-left:4px solid #d99f27;
                      border-radius:6px;
                      font-size:14px;
                    }
                    .detail-card {
                      margin:22px 0;
                      border:1px solid #eaddea;
                      border-radius:12px;
                      overflow:hidden;
                    }
                    .detail-row {
                      display:flex;
                      justify-content:space-between;
                      gap:20px;
                      padding:14px 16px;
                      border-bottom:1px solid #f0e8f1;
                    }
                    .detail-row:last-child {
                      border-bottom:0;
                    }
                    .detail-row span {
                      color:#78687e;
                    }
                    .status-danger {
                      color:#b42318;
                    }
                    .status-success {
                      color:#087443;
                    }
                  </style>
                </body>
                </html>
                """.formatted(
                escape(heading),
                content,
                Year.now().getValue()
        );
    }

    private String capitalise(String value) {
        if (value == null || value.isBlank()) {
            return "OTP";
        }

        return value.substring(0, 1).toUpperCase()
                + value.substring(1);
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber == null ? "" : accountNumber;
        }

        return "•••• " + accountNumber.substring(
                accountNumber.length() - 4
        );
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}