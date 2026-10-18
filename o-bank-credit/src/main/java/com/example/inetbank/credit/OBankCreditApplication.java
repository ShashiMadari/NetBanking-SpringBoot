package com.example.inetbank.credit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OBankCreditApplication {

	public static void main(String[] args) {
		SpringApplication.run(OBankCreditApplication.class, args);
	}

}
