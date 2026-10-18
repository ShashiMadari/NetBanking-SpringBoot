package com.inetbank.investment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import feign.RequestInterceptor;

// Retained for clients whose endpoints expect X-Internal-Service-Key.
// Identity and Banking API-key clients should use their specific configs.
public class InternalServiceClientConfig {

    @Bean
    public RequestInterceptor internalServiceKeyInterceptor(
            @Value("${app.internal.service-key}")
            String key) {

        return template -> template.header(
                "X-Internal-Service-Key",
                key
        );
    }
}