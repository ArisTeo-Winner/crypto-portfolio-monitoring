package com.mx.cryptomonitor.domain.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.repositories.PortfolioEntryRepository;
import com.mx.cryptomonitor.infrastructure.api.MarketDataService;

@Service
public class PriceUpdateService {

    private final Logger logger = LoggerFactory.getLogger(PriceUpdateService.class);

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private PortfolioEntryRepository portfolioEntryRepository;

    @Autowired
    public PriceUpdateService(MarketDataService marketDataService,
            PortfolioEntryRepository portfolioEntryRepository) {
        // TODO Auto-generated constructor stub
        this.marketDataService = marketDataService;
        this.portfolioEntryRepository = portfolioEntryRepository;
    }

    @Scheduled(fixedRate = 300000) // Cada 5 minutos (300,000 ms)
    public void updatePrices() {

        logger.info("=== Ejecutando método updatePrices() desde PriceUpdateService ===");

        List<String> symbols = portfolioEntryRepository.findDistinctAssetSymbols();
        for (String symbol : symbols) {
            Optional<BigDecimal> priceOpt = marketDataService.getCryptoPrice(symbol);
            if (priceOpt.isPresent()) {
                BigDecimal currentPrice = priceOpt.get();
                List<PortfolioEntry> entries = portfolioEntryRepository.findByAssetSymbol(symbol);
                logger.info("Entradas encontradas: " + entries.size());

                for (PortfolioEntry entry : entries) {
                    BigDecimal currentValue = entry.getTotalQuantity().multiply(currentPrice);
                    BigDecimal totalProfitLoss = currentValue.subtract(entry.getTotalInvested());
                    entry.setCurrentValue(currentPrice);
                    entry.setTotalProfitLoss(totalProfitLoss);
                    entry.setLastUpdated(LocalDateTime.now());
                    portfolioEntryRepository.save(entry);
                }
            }
        }
    }
}
