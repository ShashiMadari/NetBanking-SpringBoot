package com.example.inetbank.credit.cardmanagement;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Year;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class MerchantPurchaseReversalEmailService {

    private static final Logger LOG = LoggerFactory.getLogger(
            MerchantPurchaseReversalEmailService.class
    );

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public MerchantPurchaseReversalEmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String fromEmail) {

        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    public void send(
            String customerEmail,
            String customerName,
            String cardLastFour,
            String referenceNumber,
            String reversalReferenceNumber,
            BigDecimal refundedAmount,
            long restoredPoints,
            String reason) {

        if (customerEmail == null || customerEmail.isBlank()) {
            LOG.warn(
                    "Merchant purchase reversal email skipped: recipient is missing."
            );
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    "UTF-8"
            );

            helper.setFrom(fromEmail);
            helper.setTo(customerEmail);
            helper.setSubject(
                    "O-Bank credit-card purchase reversed"
            );
            helper.setText(
                    buildEmailHtml(
                            customerName,
                            cardLastFour,
                            referenceNumber,
                            reversalReferenceNumber,
                            refundedAmount,
                            restoredPoints,
                            reason
                    ),
                    true
            );

            mailSender.send(message);

            LOG.info(
                    "Merchant purchase reversal email sent. reference={}",
                    referenceNumber
            );

        } catch (Exception exception) {
            // The completed database reversal must remain valid even if email fails.
            LOG.error(
                    "Could not send merchant purchase reversal email. reference={}",
                    referenceNumber,
                    exception
            );
        }
    }

    private String buildEmailHtml(
            String customerName,
            String cardLastFour,
            String referenceNumber,
            String reversalReferenceNumber,
            BigDecimal refundedAmount,
            long restoredPoints,
            String reason) {

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
                        CARD PURCHASE REVERSAL
                      </div>
                    </div>

                    <div style="padding:36px;">
                      <p style="font-size:16px;line-height:1.65;color:#4c3b52;">
                        Hello %s,
                      </p>

                      <h1 style="margin:0 0 18px;font-size:25px;color:#3b2444;">
                        Your purchase has been reversed
                      </h1>

                      <div style="margin:20px 0;padding:13px 15px;color:#087443;
                                  background:#eaf8f0;border:1px solid #aee2c4;
                                  border-radius:8px;font-weight:700;">
                        Reversal completed successfully
                      </div>

                      <p style="font-size:16px;line-height:1.65;color:#4c3b52;">
                        The reversal is now reflected in your card activity.
                      </p>

                      <div style="margin:22px 0;border:1px solid #eaddea;
                                  border-radius:12px;overflow:hidden;">
                        <table style="width:100%%;border-collapse:collapse;
                                      font-size:15px;">
                          <tr>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       color:#78687e;">
                              Credit card
                            </td>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       text-align:right;font-weight:bold;">
                              •••• %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       color:#78687e;">
                              Refunded amount
                            </td>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       text-align:right;font-weight:bold;color:#087443;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       color:#78687e;">
                              Restored reward points
                            </td>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       text-align:right;font-weight:bold;color:#087443;">
                              +%d
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       color:#78687e;">
                              Original reference
                            </td>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       text-align:right;font-weight:bold;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       color:#78687e;">
                              Reversal reference
                            </td>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       text-align:right;font-weight:bold;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:14px 16px;color:#78687e;">
                              Reason
                            </td>
                            <td style="padding:14px 16px;text-align:right;font-weight:bold;">
                              %s
                            </td>
                          </tr>
                        </table>
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
                </body>
                </html>
                """.formatted(
                escape(customerName),
                escape(cardLastFour),
                formatInr(refundedAmount),
                restoredPoints,
                escape(referenceNumber),
                escape(reversalReferenceNumber),
                escape(reason),
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