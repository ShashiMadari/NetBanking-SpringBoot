package com.inetbank.banking.paymentmaintenance;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.inetbank.banking.integration.IdentitySystemNotificationClient;

@Component
public class PaymentOtpExpiryScheduler {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    PaymentOtpExpiryScheduler.class
            );

    private final ExpiredPaymentOrderRepository payments;
    private final IdentitySystemNotificationClient identitySystemNotificationClient;

    public PaymentOtpExpiryScheduler(
            ExpiredPaymentOrderRepository payments,
            IdentitySystemNotificationClient identitySystemNotificationClient) {

        this.payments = payments;
        this.identitySystemNotificationClient =
                identitySystemNotificationClient;
    }

    @Scheduled(
            cron = "${payment-otp-expiry-cleanup.cron:0 * * * * *}",
            zone = "Asia/Kolkata"
    )
    @Transactional
    public void expirePendingPaymentOtps() {

        List<ExpiredPaymentOrderRepository.ExpiredPaymentOrder>
                expiredOrders =
                payments.findExpiredPendingOrders();

        for (ExpiredPaymentOrderRepository.ExpiredPaymentOrder
                order : expiredOrders) {

            if (!payments.markExpired(order.paymentOrderId())) {
                continue;
            }

            identitySystemNotificationClient
                    .notifyPaymentOtpExpired(
                            order.customerUserId(),
                            order.paymentOrderId(),
                            order.referenceNumber()
                    );

            LOG.info(
                    "Expired pending payment OTP. "
                            + "paymentOrderId={}, reference={}",
                    order.paymentOrderId(),
                    order.referenceNumber()
            );
        }
    }
}