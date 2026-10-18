package com.inetbank.banking.fundtransfer;

import java.math.BigDecimal;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.inetbank.banking.beneficiary.Beneficiary;
import com.inetbank.banking.integration.IdentityCustomerContactClient;
import com.inetbank.banking.integration.IdentityCustomerContactResponse;
import com.inetbank.banking.integration.IdentitySystemNotificationClient;

import jakarta.mail.internet.MimeMessage;

@Service
public class RecipientTransferNotificationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    RecipientTransferNotificationService.class
            );

    private final JdbcTemplate jdbcTemplate;
    private final IdentitySystemNotificationClient notifications;
    private final IdentityCustomerContactClient customerContacts;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;

    public RecipientTransferNotificationService(
            JdbcTemplate jdbcTemplate,
            IdentitySystemNotificationClient notifications,
            IdentityCustomerContactClient customerContacts,
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from:no-reply@obank.demo}")
            String fromAddress) {

        this.jdbcTemplate = jdbcTemplate;
        this.notifications = notifications;
        this.customerContacts = customerContacts;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void notifyRecipient(
            Beneficiary beneficiary,
            BigDecimal amount,
            String referenceNumber) {

        try {
            Optional<RecipientAccount> recipient =
                    jdbcTemplate.query(
                            """
                            SELECT user_id,
                                   account_number,
                                   account_type
                              FROM accounts
                             WHERE account_number = ?
                               AND UPPER(status) = 'ACTIVE'
                            """,
                            (row, index) -> new RecipientAccount(
                                    row.getLong("user_id"),
                                    row.getString("account_number"),
                                    row.getString("account_type")
                            ),
                            beneficiary.getBeneficiaryAccountNumber()
                    ).stream().findFirst();

            if (recipient.isEmpty()) {
                return;
            }

            RecipientAccount account = recipient.get();

            IdentityCustomerContactResponse customer =
                    customerContacts.getCustomerContact(
                            account.userId()
                    );

            String accountLastFour = lastFour(
                    account.accountNumber()
            );

            notifications.createSystemNotification(
                    account.userId(),
                    "PAYMENT",
                    "INR " + amount
                            + " was credited to your "
                            + account.accountType()
                            + " account ending "
                            + accountLastFour
                            + ". Reference: "
                            + referenceNumber
            );

            sendCreditEmail(
                    customer,
                    account,
                    amount,
                    referenceNumber,
                    accountLastFour
            );

        } catch (RuntimeException exception) {
            LOG.warn(
                    "Recipient transfer notification could not be sent.",
                    exception
            );
        }
    }

    private void sendCreditEmail(
            IdentityCustomerContactResponse customer,
            RecipientAccount account,
            BigDecimal amount,
            String referenceNumber,
            String accountLastFour) {

        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            LOG.info(
                    "SMTP is unavailable; recipient credit email was not sent."
            );
            return;
        }

        try {
            MimeMessage message = sender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    "UTF-8"
            );

            helper.setFrom(fromAddress);
            helper.setTo(customer.email());
            helper.setSubject(
                    "O-Bank: INR " + amount
                            + " credited to your account"
            );

            helper.setText(
                    creditEmailHtml(
                            customer.fullName(),
                            account.accountType(),
                            accountLastFour,
                            amount,
                            referenceNumber
                    ),
                    true
            );

            sender.send(message);

        } catch (Exception exception) {
            LOG.warn(
                    "Recipient credit email delivery failed.",
                    exception
            );
        }
    }

    private String creditEmailHtml(
            String fullName,
            String accountType,
            String accountLastFour,
            BigDecimal amount,
            String referenceNumber) {

        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f7f3f7;
                             font-family:Arial,sans-serif;color:#2f2034;">
                  <div style="max-width:620px;margin:32px auto;background:#ffffff;
                              border-radius:18px;overflow:hidden;
                              border:1px solid #eadbea;">

                    <div style="background:#74417b;padding:30px 36px;">
                      <h1 style="margin:0;color:#ffffff;font-size:26px;">
                        O-Bank
                      </h1>
                      <p style="margin:8px 0 0;color:#f4def5;font-size:15px;">
                        Account credit alert
                      </p>
                    </div>

                    <div style="padding:34px 36px;">
                      <p style="font-size:17px;margin:0 0 18px;">
                        Hello, %s
                      </p>

                      <h2 style="margin:0 0 10px;color:#24734f;font-size:25px;">
                        INR %s credited successfully
                      </h2>

                      <p style="margin:0 0 26px;line-height:1.6;color:#5d5260;">
                        Funds have been credited to your %s account ending
                        <strong>%s</strong>.
                      </p>

                      <div style="background:#f8f1f8;border:1px solid #e8d7e8;
                                  border-radius:12px;padding:20px;">
                        <table style="width:100%%;border-collapse:collapse;
                                      font-size:15px;">
                          <tr>
                            <td style="padding:7px 0;color:#766779;">
                              Amount credited
                            </td>
                            <td style="padding:7px 0;text-align:right;
                                       color:#24734f;font-weight:bold;">
                              INR %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:7px 0;color:#766779;">
                              Account
                            </td>
                            <td style="padding:7px 0;text-align:right;
                                       font-weight:bold;">
                              %s · •••• %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:7px 0;color:#766779;">
                              Reference
                            </td>
                            <td style="padding:7px 0;text-align:right;
                                       font-weight:bold;">
                              %s
                            </td>
                          </tr>
                        </table>
                      </div>

                      <p style="margin:26px 0 0;line-height:1.6;color:#766779;
                                font-size:14px;">
                        You can review this credit in O-Bank under
                        <strong>Transactions</strong>.
                      </p>
                    </div>

                    <div style="background:#fbf8fb;padding:20px 36px;
                                border-top:1px solid #eee3ee;">
                      <p style="margin:0;color:#8a7b8b;font-size:12px;">
                        This is an automated O-Bank account notification.
                      </p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                        escapeHtml(fullName),
                        amount,
                        escapeHtml(accountType),
                        accountLastFour,
                        amount,
                        escapeHtml(accountType),
                        accountLastFour,
                        escapeHtml(referenceNumber)
                );
    }

    private String lastFour(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber == null ? "" : accountNumber;
        }

        return accountNumber.substring(
                accountNumber.length() - 4
        );
    }

    private String escapeHtml(String value) {
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

    private record RecipientAccount(
            Long userId,
            String accountNumber,
            String accountType
    ) {
    }
}