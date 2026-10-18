package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Year;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class CreditCardEmiPaymentEmailService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    CreditCardEmiPaymentEmailService.class
            );

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;

    public CreditCardEmiPaymentEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${mail.from:no-reply@obank.demo}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendOtp(
            String customerEmail,
            String otp,
            String lastFour,
            BigDecimal amount) {

        String content = """
                <p>You requested a payment for your O-Bank credit-card EMI.</p>
                <p>Use the verification code below to authorise the EMI payment:</p>

                <div class="otp-box">%s</div>

                %s

                <p>This OTP expires in <strong>5 minutes</strong>.</p>

                <p class="security-note">
                    Do not share this OTP with anyone. O-Bank will never ask
                    you to disclose this code.
                </p>
                """.formatted(
                escape(otp),
                emiDetails(lastFour, amount, null)
        );

        sendHtml(
                customerEmail,
                "O-Bank EMI payment verification",
                "Verify your EMI payment",
                content
        );
    }

    public void sendSuccess(
            String customerEmail,
            String lastFour,
            BigDecimal amount,
            String referenceNumber) {

        String content = """
                <p>Your credit-card EMI payment has been completed successfully.</p>

                <div class="success-banner">
                    EMI payment completed successfully
                </div>

                %s

                <p>
                    Your EMI schedule and remaining principal have been updated.
                    You can review them under <strong>Credit Card EMI</strong>
                    in O-Bank.
                </p>
                """.formatted(
                emiDetails(lastFour, amount, referenceNumber)
        );

        sendHtml(
                customerEmail,
                "O-Bank EMI payment completed",
                "Your EMI payment was successful",
                content
        );
    }

    private String emiDetails(
            String lastFour,
            BigDecimal amount,
            String referenceNumber) {

        String referenceRow = referenceNumber == null
                ? ""
                : """
                  <div class="detail-row">
                    <span>O-Bank reference</span>
                    <strong>%s</strong>
                  </div>
                  """.formatted(escape(referenceNumber));

        return """
                <div class="detail-card">
                    <div class="detail-row">
                        <span>EMI payment amount</span>
                        <strong class="amount-success">%s</strong>
                    </div>
                    <div class="detail-row">
                        <span>Credit card</span>
                        <strong>•••• %s</strong>
                    </div>
                    %s
                </div>
                """.formatted(
                formatInr(amount),
                escape(lastFour),
                referenceRow
        );
    }

    private void sendHtml(
            String recipient,
            String subject,
            String heading,
            String content) {

        if (recipient == null || recipient.isBlank()) {
            LOG.warn("Credit-card EMI email skipped: recipient is missing.");
            return;
        }

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            LOG.info(
                    "SMTP is unavailable; credit-card EMI email was not sent. subject={}",
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
                    "Credit-card EMI email delivery failed. subject={}",
                    subject,
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
                        CREDIT-CARD EMI PAYMENT
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
                    .security-note {
                      padding:13px 15px;
                      background:#fff8e8;
                      border-left:4px solid #d99f27;
                      border-radius:6px;
                      font-size:14px;
                    }
                    .success-banner {
                      margin:20px 0;
                      padding:13px 15px;
                      color:#087443;
                      background:#eaf8f0;
                      border:1px solid #aee2c4;
                      border-radius:8px;
                      font-weight:700;
                    }
                    .amount-success {
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

    private String formatInr(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(
                new Locale("en", "IN")
        );
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "INR " + formatter.format(
                amount == null ? BigDecimal.ZERO : amount
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