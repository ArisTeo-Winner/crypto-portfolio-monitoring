package com.mx.cryptomonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CryptoPortfolioMonitoringApplication {

	public static void main(String[] args) {
        System.out.println("DATABASE_URL: " + System.getenv("DATABASE_URL"));
        System.out.println("DATABASE_USERNAME: " + System.getenv("DATABASE_USERNAME"));
        System.out.println("DATABASE_PASSWORD: " + System.getenv("DATABASE_PASSWORD"));
		SpringApplication.run(CryptoPortfolioMonitoringApplication.class, args);
	}

}
