package com.example.inetbank.forex.admin;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.inetbank.forex.event.ForexKafkaEventPublisher;
import com.example.inetbank.forex.event.ForexKafkaEventPublisher.ForexIdentityEvent;

@Service
public class AdminForexKycService {

    private final AdminForexKycRepository repository;
    private final ForexKafkaEventPublisher kafkaEvents;

    public AdminForexKycService(
            AdminForexKycRepository repository,
            ForexKafkaEventPublisher kafkaEvents) {

        this.repository = repository;
        this.kafkaEvents = kafkaEvents;
    }

    @Transactional
    public void updateKyc(
            Long adminUserId,
            Long customerUserId,
            KycStatusRequest request) {

        String status = request.status()
                .trim()
                .toUpperCase(Locale.ROOT);

        repository.save(
                customerUserId,
                adminUserId,
                status
        );

        String message = "APPROVED".equals(status)
                ? "Your Forex KYC verification has been approved. "
                        + "You can now use Forex services."
                : "Your Forex KYC status has been updated to "
                        + status + ".";

        ForexIdentityEvent event = new ForexIdentityEvent(
                customerUserId,
                adminUserId,
                "SECURITY",
                message,
                "FOREX_KYC_STATUS_UPDATED",
                "FOREX_KYC",
                String.valueOf(customerUserId),
                LocalDateTime.now()
        );

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        kafkaEvents.publish(
                                event,
                                "FOREX-KYC-" + customerUserId
                                        + "-" + status
                        );
                    }
                }
        );
    }
}
