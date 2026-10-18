package com.inetbank.investment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import feign.RequestInterceptor;

// No @Configuration: loaded only by the referencing Feign client.
public class InternalApiKeyFeignConfig {

    @Bean
    public RequestInterceptor identityApiKeyInterceptor(
            @Value("${app.internal.identity-api-key}")
            String apiKey) {

        return template -> template.header(
                "X-Internal-Api-Key",
                apiKey
        );
    }
}