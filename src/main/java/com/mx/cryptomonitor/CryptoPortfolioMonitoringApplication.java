package com.mx.cryptomonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptoPortfolioMonitoringApplication {

  public static void main(String[] args) {

    SpringApplication.run(CryptoPortfolioMonitoringApplication.class, args);
  }
}
