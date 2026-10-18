package com.example.inetbank.credit.cardmanagement;

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
public class CardPinChangeEmailService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    CardPinChangeEmailService.class
            );

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;

    public CardPinChangeEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${mail.from:no-reply@obank.demo}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendOtp(
            String customerEmail,
            String otp,
            CardPinChangeRepository.CardDetails card,
            boolean resent) {

        String intro = resent
                ? "A replacement OTP was requested for changing your card PIN."
                : "You requested a change to your O-Bank card PIN.";

        String content = """
                <p>%s</p>
                <p>Use this verification code to continue:</p>

                <div class="otp-box">%s</div>

                %s

                <p>This OTP expires in <strong>5 minutes</strong>.</p>

                <p class="security-note">
                    Never share your OTP or card PIN with anyone, including
                    O-Bank staff.
                </p>
                """.formatted(
                intro,
                escape(otp),
                cardDetails(card)
        );

        sendHtml(
                customerEmail,
                resent
                        ? "O-Bank card PIN change OTP resent"
                        : "O-Bank card PIN change verification",
                "Verify your card PIN change",
                content
        );
    }

    public void sendSuccess(
            String customerEmail,
            CardPinChangeRepository.CardDetails card) {

        String content = """
                <p>The PIN for your O-Bank card has been changed successfully.</p>

                <div class="success-banner">
                    Card PIN changed successfully
                </div>

                %s

                <p class="security-note">
                    If you did not make this change, block the card immediately
                    in O-Bank and contact O-Bank support.
                </p>
                """.formatted(cardDetails(card));

        sendHtml(
                customerEmail,
                "O-Bank card PIN changed successfully",
                "Your card PIN was changed",
                content
        );
    }

    private String cardDetails(
            CardPinChangeRepository.CardDetails card) {

        return """
                <div class="detail-card">
                    <div class="detail-row">
                        <span>Card type</span>
                        <strong>%s</strong>
                    </div>
                    <div class="detail-row">
                        <span>Card number</span>
                        <strong>•••• %s</strong>
                    </div>
                </div>
                """.formatted(
                escape(card.cardType()),
                escape(card.lastFour())
        );
    }

    private void sendHtml(
            String recipient,
            String subject,
            String heading,
            String content) {

        if (recipient == null || recipient.isBlank()) {
            LOG.warn(
                    "Card PIN email was skipped because customer email is missing."
            );
            return;
        }

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            LOG.info(
                    "SMTP is unavailable; card PIN email was not sent. subject={}",
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
                    "Card PIN email delivery failed. subject={}",
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
                        CARD SECURITY
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
                      This is an automated O-Bank security message. Please do
                      not reply to this email.<br>
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
                  </style>
                </body>
                </html>
                """.formatted(
                escape(heading),
                content,
                Year.now().getValue()
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