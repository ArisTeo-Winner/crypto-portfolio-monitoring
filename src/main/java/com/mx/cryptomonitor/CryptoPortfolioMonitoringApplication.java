package com.mx.cryptomonitor;

import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptoPortfolioMonitoringApplication {

  private static final Logger log =
      LoggerFactory.getLogger(CryptoPortfolioMonitoringApplication.class);

  public static void main(String[] args) {
    SpringApplication.run(CryptoPortfolioMonitoringApplication.class, args);
    log.info("JVM timezone={}", ZoneId.systemDefault());
  }
}
