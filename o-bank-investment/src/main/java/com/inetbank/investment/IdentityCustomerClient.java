package com.inetbank.investment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        contextId = "investmentIdentityCustomerClient",
        name = "o-bank-identity",
        path = "/internal/customers",
        configuration = InternalApiKeyFeignConfig.class
)
public interface IdentityCustomerClient {

    @GetMapping("/by-email")
    CustomerProfileResponse findByEmail(
            @RequestParam("email") String email
    );

    record CustomerProfileResponse(
            Long userId,
            String fullName,
            String email,
            String role,
            String status
    ) {
    }
}