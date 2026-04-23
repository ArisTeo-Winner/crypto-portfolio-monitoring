package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.AssetPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;

@Service
@ConditionalOnProperty(
    value = "portfolio.price-update.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PriceUpdateService {

  private final Logger logger = LoggerFactory.getLogger(PriceUpdateService.class);
  private final MarketDataProvider marketDataProvider;
  private final AssetPricePort assetPricePort;
  private final PortfolioEntryRepository portfolioEntryRepository;

  public PriceUpdateService(
      MarketDataProvider marketDataProvider,
      AssetPricePort assetPricePort,
      PortfolioEntryRepository portfolioEntryRepository) {
    this.marketDataProvider = marketDataProvider;
    this.assetPricePort = assetPricePort;
    this.portfolioEntryRepository = portfolioEntryRepository;
  }

  @Scheduled(fixedRate = 300000)
  public void updatePrices() {

    for (PortfolioEntry entry : portfolioEntryRepository.findAll()) {
      Optional<BigDecimal> priceOpt = resolveCurrentAssetPrice(entry);
      if (priceOpt.isPresent()) {
        BigDecimal currentPrice = priceOpt.get();
        BigDecimal currentValue = entry.getTotalQuantity().multiply(currentPrice);
        BigDecimal totalProfitLoss = currentValue.subtract(entry.getTotalInvested());
        entry.setCurrentValue(currentValue);
        entry.setTotalProfitLoss(totalProfitLoss);
        entry.setLastUpdated(LocalDateTime.now());
        portfolioEntryRepository.save(entry);
      }
    }
  }

  private Optional<BigDecimal> resolveCurrentAssetPrice(PortfolioEntry entry) {
    if ("CRYPTO".equalsIgnoreCase(entry.getAssetType())) {
      return assetPricePort.getCryptoPriceAmount(entry.getAssetSymbol()).blockOptional();
    }
    return marketDataProvider.getLatest(entry.getAssetSymbol());
  }
}
