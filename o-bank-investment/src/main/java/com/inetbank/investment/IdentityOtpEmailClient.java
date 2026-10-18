package com.inetbank.investment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        contextId = "investmentIdentityOtpEmailClient",
        name = "o-bank-identity",
        path = "/internal/emails",
        configuration = InternalApiKeyFeignConfig.class
)
public interface IdentityOtpEmailClient {

    @PostMapping("/otp")
    void sendOtp(@RequestBody OtpEmailRequest request);

    // Resend uses the same delivery endpoint with a freshly generated OTP.
    default void resendOtp(OtpEmailRequest request) {
        sendOtp(request);
    }

    record OtpEmailRequest(
            String email,
            String otp,
            String purpose
    ) {
    }
}