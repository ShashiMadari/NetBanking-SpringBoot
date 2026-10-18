package com.inetbank.banking.beneficiary;

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
public class BeneficiaryEmailService {

    private static final Logger LOG =
            LoggerFactory.getLogger(BeneficiaryEmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;

    BeneficiaryEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from:no-reply@obank.demo}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendPendingEmail(
            String customerEmail,
            Beneficiary beneficiary) {

        String content = """
                <p>Your beneficiary request has been received and is waiting
                for administrator approval.</p>

                %s

                <p>You can transfer money to this beneficiary once the request
                is approved.</p>
                """.formatted(beneficiaryDetails(beneficiary));

        sendHtml(
                customerEmail,
                "O-Bank beneficiary approval pending",
                "Beneficiary request received",
                "PENDING APPROVAL",
                "status-pending",
                content
        );
    }

    public void sendApprovedEmail(
            String customerEmail,
            Beneficiary beneficiary) {

        String content = """
                <p>Your beneficiary has been approved. You can now make
                transfers to this account using O-Bank.</p>

                %s

                <p>Open <strong>Send Money</strong> in O-Bank to begin a
                transfer.</p>
                """.formatted(beneficiaryDetails(beneficiary));

        sendHtml(
                customerEmail,
                "O-Bank beneficiary approved",
                "Your beneficiary is ready",
                "APPROVED",
                "status-success",
                content
        );
    }

    public void sendRejectedEmail(
            String customerEmail,
            Beneficiary beneficiary) {

        String content = """
                <p>Your beneficiary request could not be approved.</p>

                %s

                <p>If the account details are correct, please add the
                beneficiary again or contact O-Bank support for help.</p>
                """.formatted(beneficiaryDetails(beneficiary));

        sendHtml(
                customerEmail,
                "O-Bank beneficiary request rejected",
                "Beneficiary request not approved",
                "REJECTED",
                "status-danger",
                content
        );
    }

    public void sendBlockedEmail(
            String customerEmail,
            Beneficiary beneficiary) {

        String content = """
                <p>This beneficiary has been blocked and cannot receive
                transfers from your O-Bank account.</p>

                %s

                <p>If you did not expect this change, contact O-Bank support
                immediately.</p>
                """.formatted(beneficiaryDetails(beneficiary));

        sendHtml(
                customerEmail,
                "O-Bank beneficiary blocked",
                "Beneficiary has been blocked",
                "BLOCKED",
                "status-danger",
                content
        );
    }

    private String beneficiaryDetails(Beneficiary beneficiary) {
        return """
                <div class="detail-card">
                    <div class="detail-row">
                        <span>Beneficiary name</span>
                        <strong>%s</strong>
                    </div>
                    <div class="detail-row">
                        <span>Account number</span>
                        <strong>%s</strong>
                    </div>
                    <div class="detail-row">
                        <span>IFSC code</span>
                        <strong>%s</strong>
                    </div>
                </div>
                """.formatted(
                escape(beneficiary.getBeneficiaryName()),
                escape(maskAccountNumber(
                        beneficiary.getBeneficiaryAccountNumber()
                )),
                escape(beneficiary.getIfscCode())
        );
    }

    private void sendHtml(
            String recipient,
            String subject,
            String heading,
            String status,
            String statusClass,
            String content) {

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            LOG.info(
                    "SMTP is unavailable; beneficiary email was not sent. subject={}",
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
            helper.setText(
                    buildEmailHtml(
                            heading,
                            status,
                            statusClass,
                            content
                    ),
                    true
            );

            sender.send(message);

        } catch (Exception exception) {
            LOG.warn(
                    "Beneficiary email delivery failed. subject={}",
                    subject,
                    exception
            );
        }
    }

    private String buildEmailHtml(
            String heading,
            String status,
            String statusClass,
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
                        BENEFICIARY MANAGEMENT
                      </div>
                    </div>

                    <div style="padding:36px;">
                      <div class="status-badge %s">%s</div>

                      <h1 style="margin:16px 0 22px;font-size:25px;color:#3b2444;">
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
                    .status-badge {
                      display:inline-block;
                      padding:7px 11px;
                      border-radius:20px;
                      font-size:12px;
                      font-weight:700;
                      letter-spacing:.5px;
                    }
                    .status-pending {
                      color:#8a5c00;
                      background:#fff3d9;
                    }
                    .status-success {
                      color:#087443;
                      background:#eaf8f0;
                    }
                    .status-danger {
                      color:#b42318;
                      background:#fff0ef;
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
                  </style>
                </body>
                </html>
                """.formatted(
                escape(statusClass),
                escape(status),
                escape(heading),
                content,
                Year.now().getValue()
        );
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