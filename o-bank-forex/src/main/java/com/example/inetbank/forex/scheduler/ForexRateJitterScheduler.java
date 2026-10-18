package com.example.inetbank.forex.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ForexRateJitterScheduler {

    private final ForexRateJitterService rateJitterService;
    private final boolean enabled;

    public ForexRateJitterScheduler(
            ForexRateJitterService rateJitterService,
            @Value("${app.forex.rate-jitter-enabled:false}")
            boolean enabled) {

        this.rateJitterService = rateJitterService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.forex.rate-jitter-ms:60000}"
    )
    public void updateSimulatedRates() {

        if (enabled) {
            rateJitterService.updateRates();
        }
    }
}