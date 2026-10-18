package com.example.inetbank.credit.cardmanagement;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.inetbank.credit.integration.IdentityCustomerEmailClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;
import com.example.inetbank.credit.integration.IdentityScheduledNotificationClient;

@Component
public class CreditCardEmiReminderScheduler {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    CreditCardEmiReminderScheduler.class
            );

    private static final ZoneId INDIA =
            ZoneId.of("Asia/Kolkata");

    private final CreditCardEmiReminderRepository reminders;
    private final CreditCardEmiReminderEmailService emailService;
    private final IdentityCustomerEmailClient identityCustomerEmailClient;
    private final IdentitySystemNotificationClient audits;
    private final IdentityScheduledNotificationClient notifications;
    public CreditCardEmiReminderScheduler(
            CreditCardEmiReminderRepository reminders,
            CreditCardEmiReminderEmailService emailService,
            IdentityCustomerEmailClient identityCustomerEmailClient,
            IdentitySystemNotificationClient audits,
            IdentityScheduledNotificationClient notifications) {

        this.reminders = reminders;
        this.emailService = emailService;
        this.identityCustomerEmailClient =
                identityCustomerEmailClient;
        this.audits = audits;
        this.notifications = notifications;
    }

    @Scheduled(
            cron = "${credit-card-emi-reminder-scheduler.cron:0 0 9 * * *}",
            zone = "Asia/Kolkata"
    )
    public void sendThreeDayReminders() {

        LocalDate dueDate = LocalDate.now(INDIA)
                .plusDays(3);

        List<CreditCardEmiReminderRepository
                .EmiReminderCandidate> candidates =
                reminders.findDueForReminder(dueDate);

        LOG.info(
                "Starting credit-card EMI reminders. "
                        + "dueDate={}, installments={}",
                dueDate,
                candidates.size()
        );

        for (CreditCardEmiReminderRepository
                .EmiReminderCandidate reminder : candidates) {

            sendReminder(reminder);
        }
    }

    private void sendReminder(
            CreditCardEmiReminderRepository
                    .EmiReminderCandidate reminder) {

        try {
            String customerEmail =
                    identityCustomerEmailClient.getCustomerEmail(
                            reminder.userId()
                    );

            boolean sent = emailService.send(
                    customerEmail,
                    reminder
            );

            if (sent) {
                reminders.recordEmailReminder(
                        reminder.scheduleId(),
                        reminder.dueDate()
                );

                audits.createAuditEvent(
                        reminder.userId(),
                        "CREDIT_CARD_EMI_REMINDER_EMAIL_SENT",
                        "CREDIT_CARD_EMI_SCHEDULE",
                        String.valueOf(reminder.scheduleId())
                );


                LOG.info(
                        "Credit-card EMI reminder delivered. "
                                + "scheduleId={}, dueDate={}",
                        reminder.scheduleId(),
                        reminder.dueDate()
                );
            }
            notifications.createNotification(
                    reminder.userId(),
                    "PAYMENT",
                    "Reminder: your credit-card EMI installment of INR "
                            + reminder.emiAmount()
                            + " for card XXXX"
                            + reminder.cardLastFour()
                            + " is due on "
                            + reminder.dueDate()
                            + "."
            );
        } catch (RuntimeException exception) {
            LOG.warn(
                    "Credit-card EMI reminder delivery failed. "
                            + "scheduleId={}, dueDate={}",
                    reminder.scheduleId(),
                    reminder.dueDate(),
                    exception
            );
        }
    }
}