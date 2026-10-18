package com.inetbank.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableScheduling
@SpringBootApplication
public class OBackBankingApplication {

	public static void main(String[] args) {
		SpringApplication.run(OBackBankingApplication.class, args);
	}

}
