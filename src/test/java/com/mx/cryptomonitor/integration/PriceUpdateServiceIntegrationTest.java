package com.mx.cryptomonitor.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.portfolio.application.service.PriceUpdateService;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest(properties = "portfolio.price-update.enabled=true")
@ActiveProfiles("test")
class PriceUpdateServiceIntegrationTest {

  private static final Logger logger =
      LoggerFactory.getLogger(PriceUpdateServiceIntegrationTest.class);

  @Autowired private PriceUpdateService service;
  @Autowired private PortfolioEntryRepository repository;
  @Autowired private UserRepository userRepository;

  @org.springframework.boot.test.mock.mockito.MockBean
  private MarketDataProvider marketDataProvider;

  @org.springframework.boot.test.mock.mockito.MockBean
  private com.mx.cryptomonitor.marketdata.application.port.out.AssetPricePort assetPricePort;

  private final String SYMBOL_ETH = "ETH";

  @Test
  public void testUpdatePrices_integration() {
    logger.info(
        "=== Ejecutando metodo testUpdatePrices_integration() desde PriceUpdateServiceIntegrationTest ===");

    User user = new User();
    user.setUsername("testUser_" + System.currentTimeMillis());
    user.setEmail("test_" + System.currentTimeMillis() + "@example.com");
    user.setPasswordHash("test@123");

    User savedUser = userRepository.save(user);
    logger.info("Datos de usuario mapeado:{}", savedUser);

    PortfolioEntry entry =
        PortfolioEntry.builder()
            .userId(savedUser.getId())
            .assetSymbol("ETH")
            .assetType("CRYPTO")
            .totalQuantity(BigDecimal.valueOf(2.0))
            .totalInvested(BigDecimal.valueOf(5000))
            .averagePricePerUnit(BigDecimal.valueOf(2500))
            .lastUpdated(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    entry.setTotalInvested(BigDecimal.valueOf(502.06));
    logger.info("Datos mapeado de PortfolioEntry {}", repository.save(entry));

    org.mockito.Mockito.when(marketDataProvider.getLatest(SYMBOL_ETH))
        .thenReturn(Optional.of(BigDecimal.valueOf(3000.0)));
    org.mockito.Mockito.when(assetPricePort.getCryptoPriceAmount(SYMBOL_ETH))
        .thenReturn(reactor.core.publisher.Mono.just(BigDecimal.valueOf(3000.0)));

    Optional<BigDecimal> priceOpt = marketDataProvider.getLatest(SYMBOL_ETH);
    BigDecimal currentPrice = priceOpt.get();

    service.updatePrices();
    PortfolioEntry updatedEntry = repository.findByAssetSymbol(SYMBOL_ETH).get(0);

    logger.info(
        "Valor actual del activo (calculado en tiempo real); {}: {} ",
        currentPrice,
        updatedEntry.getCurrentValue());
  }
}
