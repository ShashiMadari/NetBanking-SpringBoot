package com.example.inetbank.credit.loan;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
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
public class LoanEmiReminderEmailService {

    private static final Logger LOG = LoggerFactory.getLogger(
            LoanEmiReminderEmailService.class
    );

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;

    public LoanEmiReminderEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from:no-reply@obank.demo}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public boolean send(
            String customerEmail,
            String customerName,
            String loanAccountNumber,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal emiAmount) {

        if (customerEmail == null || customerEmail.isBlank()) {
            LOG.warn("Loan EMI reminder skipped: recipient is missing.");
            return false;
        }

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            LOG.info("SMTP is unavailable; loan EMI reminder was not sent.");
            return false;
        }

        try {
            MimeMessage message = sender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    "UTF-8"
            );

            helper.setFrom(fromAddress);
            helper.setTo(customerEmail);
            helper.setSubject("O-Bank loan EMI payment reminder");
            helper.setText(
                    buildEmailHtml(
                            customerName,
                            loanAccountNumber,
                            installmentNumber,
                            dueDate,
                            emiAmount
                    ),
                    true
            );

            sender.send(message);
            return true;

        } catch (Exception exception) {
            LOG.error(
                    "Loan EMI reminder email failed. dueDate={}",
                    dueDate,
                    exception
            );
            return false;
        }
    }

    private String buildEmailHtml(
            String customerName,
            String loanAccountNumber,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal emiAmount) {

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
                        LOAN EMI REMINDER
                      </div>
                    </div>

                    <div style="padding:36px;">
                      <p style="font-size:16px;line-height:1.65;color:#4c3b52;">
                        Hello %s,
                      </p>

                      <h1 style="margin:0 0 18px;font-size:25px;color:#3b2444;">
                        Your EMI payment is due soon
                      </h1>

                      <div style="margin:20px 0;padding:13px 15px;color:#8a5c00;
                                  background:#fff3d9;border:1px solid #f0d496;
                                  border-radius:8px;font-weight:700;">
                        Your EMI is due in 3 days
                      </div>

                      <p style="font-size:16px;line-height:1.65;color:#4c3b52;">
                        Please ensure sufficient funds are available in your
                        selected payment account before the due date.
                      </p>

                      <div style="margin:22px 0;border:1px solid #eaddea;
                                  border-radius:12px;overflow:hidden;">
                        <table style="width:100%%;border-collapse:collapse;
                                      font-size:15px;">
                          <tr>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       color:#78687e;">
                              Loan account
                            </td>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       text-align:right;font-weight:bold;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       color:#78687e;">
                              Instalment number
                            </td>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       text-align:right;font-weight:bold;">
                              %d
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       color:#78687e;">
                              Due date
                            </td>
                            <td style="padding:14px 16px;border-bottom:1px solid #f0e8f1;
                                       text-align:right;font-weight:bold;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:14px 16px;color:#78687e;">
                              EMI amount
                            </td>
                            <td style="padding:14px 16px;text-align:right;
                                       font-weight:bold;color:#b35d16;">
                              %s
                            </td>
                          </tr>
                        </table>
                      </div>

                      <p style="font-size:15px;line-height:1.65;color:#4c3b52;">
                        Open <strong>Loans</strong> in O-Bank to view your EMI
                        schedule or make an early payment.
                      </p>
                    </div>

                    <div style="padding:20px 36px;background:#fbf8fb;
                                border-top:1px solid #eee3ee;
                                font-size:12px;line-height:1.55;color:#78687e;">
                      This is an automated O-Bank reminder. Please do not reply
                      to this email.<br>
                      © %d O-Bank. Secure banking made simple.
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                escape(customerName),
                escape(maskLoanNumber(loanAccountNumber)),
                installmentNumber,
                formatDate(dueDate),
                formatInr(emiAmount),
                Year.now().getValue()
        );
    }

    private String maskLoanNumber(String loanAccountNumber) {
        if (loanAccountNumber == null || loanAccountNumber.length() <= 4) {
            return loanAccountNumber == null ? "" : loanAccountNumber;
        }

        return "•••• " + loanAccountNumber.substring(
                loanAccountNumber.length() - 4
        );
    }

    private String formatDate(LocalDate date) {
        return date == null ? "Not available" : DATE_FORMAT.format(date);
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