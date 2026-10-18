package com.inetbank.investment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        contextId = "investmentIdentityAuditClient",
        name = "o-bank-identity",
        path = "/internal/system/audit-events",
        configuration = InternalApiKeyFeignConfig.class
)
public interface IdentityAuditClient {

    @PostMapping
    void create(@RequestBody CreateAuditEventRequest request);

    record CreateAuditEventRequest(
            Long userId,
            String action,
            String entityType,
            String entityId,
            String ipAddress
    ) {
    }
}