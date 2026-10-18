package com.inetbank.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka // KAFKA CHANGE: enables @KafkaListener methods.
public class OBackIdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                OBackIdentityApplication.class,
                args
        );
    }
}