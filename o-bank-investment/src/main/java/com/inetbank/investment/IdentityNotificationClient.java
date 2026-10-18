package com.inetbank.investment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        contextId = "investmentIdentityNotificationClient",
        name = "o-bank-identity",
        path = "/internal/notifications",
        configuration = InternalApiKeyFeignConfig.class
)
public interface IdentityNotificationClient {

    @PostMapping("/system")
    void create(@RequestBody NotificationRequest request);

    record NotificationRequest(
            Long userId,
            String eventType,
            String message
    ) {
    }
}