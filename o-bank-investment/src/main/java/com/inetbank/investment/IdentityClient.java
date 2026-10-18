package com.inetbank.investment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        contextId = "investmentIdentityUserClient",
        name = "o-bank-identity",
        path = "/internal/customers",
        configuration = InternalApiKeyFeignConfig.class
)
public interface IdentityClient {

    @GetMapping("/by-email")
    IdentityUserResponse findUserByEmail(
            @RequestParam("email") String email
    );

    record IdentityUserResponse(
            Long userId,
            String fullName,
            String email,
            String role,
            String status
    ) {
    }
}