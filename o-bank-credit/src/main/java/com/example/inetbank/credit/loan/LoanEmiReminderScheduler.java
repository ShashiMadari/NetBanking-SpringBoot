package com.example.inetbank.credit.loan;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.inetbank.credit.integration.IdentityCustomerEmailClient;
import com.example.inetbank.credit.integration.IdentityCustomerEmailResponse;
import com.example.inetbank.credit.integration.IdentityScheduledNotificationClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;

@Component
public class LoanEmiReminderScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(
            LoanEmiReminderScheduler.class
    );

    private final LoanEmiReminderRepository reminders;
    private final LoanEmiReminderEmailService emailService;
    private final IdentityCustomerEmailClient customerEmailClient;
    private final IdentityScheduledNotificationClient notifications;
    private final IdentitySystemNotificationClient audits;

    public LoanEmiReminderScheduler(
            LoanEmiReminderRepository reminders,
            LoanEmiReminderEmailService emailService,
            IdentityCustomerEmailClient customerEmailClient,
            IdentityScheduledNotificationClient notifications,
            IdentitySystemNotificationClient audits) {

        this.reminders = reminders;
        this.emailService = emailService;
        this.customerEmailClient = customerEmailClient;
        this.notifications = notifications;
        this.audits = audits;
    }

    @Scheduled(
            cron = "${loan-emi-reminder-scheduler.cron:0 20 9 * * *}",
            zone = "Asia/Kolkata"
    )
    public void sendThreeDayReminders() {

        LocalDate dueDate = LocalDate.now().plusDays(3);

        List<LoanEmiReminderRepository.DueEmiReminder> schedules =
                reminders.findDueOn(dueDate);

        LOG.info(
                "Starting loan EMI reminders. dueDate={}, installments={}",
                dueDate,
                schedules.size()
        );

        for (LoanEmiReminderRepository.DueEmiReminder schedule
                : schedules) {

            sendReminder(schedule);
        }
    }

    private void sendReminder(
            LoanEmiReminderRepository.DueEmiReminder schedule) {

        if (reminders.alreadySent(schedule.scheduleId())) {
            return;
        }

        try {
            IdentityCustomerEmailResponse customer =
                    customerEmailClient.getCustomerContact(
                            schedule.customerUserId()
                    );

            boolean sent = emailService.send(
                    customer.email(),
                    customer.fullName(),
                    schedule.loanAccountNumber(),
                    schedule.installmentNumber(),
                    schedule.dueDate(),
                    schedule.emiAmount()
            );

            if (!sent) {
                return;
            }

            reminders.recordEmailReminder(
                    schedule.scheduleId(),
                    schedule.dueDate()
            );

            safeNotification(
                    schedule.customerUserId(),
                    "LOAN",
                    "Loan EMI payment reminder: INR "
                            + schedule.emiAmount().toPlainString()
                            + " is due on "
                            + schedule.dueDate()
                            + ". Loan account: "
                            + schedule.loanAccountNumber()
            );

            safeAudit(
                    schedule.customerUserId(),
                    "LOAN_EMI_REMINDER_EMAIL_SENT",
                    "LOAN_EMI_SCHEDULE",
                    schedule.scheduleId()
            );

            LOG.info(
                    "Loan EMI reminder delivered. "
                            + "scheduleId={}, dueDate={}",
                    schedule.scheduleId(),
                    schedule.dueDate()
            );
        } catch (RuntimeException exception) {
            LOG.error(
                    "Loan EMI reminder failed. scheduleId={}",
                    schedule.scheduleId(),
                    exception
            );
        }
    }

    private void safeNotification(
            Long userId,
            String eventType,
            String message) {

        try {
            notifications.createNotification(
                    userId,
                    eventType,
                    message
            );
        } catch (RuntimeException exception) {
            LOG.error(
                    "Loan EMI reminder notification failed. userId={}",
                    userId,
                    exception
            );
        }
    }

    private void safeAudit(
            Long userId,
            String action,
            String entityType,
            Long entityId) {

        try {
            audits.createAuditEvent(
                    userId,
                    action,
                    entityType,
                    String.valueOf(entityId)
            );
        } catch (RuntimeException exception) {
            LOG.error(
                    "Loan EMI reminder audit event failed. scheduleId={}",
                    entityId,
                    exception
            );
        }
    }
}