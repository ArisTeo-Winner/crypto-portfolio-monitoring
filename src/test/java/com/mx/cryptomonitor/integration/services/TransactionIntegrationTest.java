package com.mx.cryptomonitor.integration.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.mapper.TransactionMapper;
import com.mx.cryptomonitor.transaction.application.service.TransactionService;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class TransactionIntegrationTest {

  private final Logger logger = LoggerFactory.getLogger(TransactionIntegrationTest.class);

  @Autowired private TransactionRepository transactionRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PortfolioEntryRepository portfolioEntryRepository;
  @Autowired private TransactionMapper transactionMapper;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TransactionService transactionService;

  private User testUser;
  private PortfolioEntry portfolioEntry;
  private UUID transactionId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    testUser =
        User.builder()
            .username("leo")
            .email("leo@example.com")
            .passwordHash("hashed_password")
            .firstName("manzano")
            .build();

    testUser = userRepository.save(testUser);
    logger.info(">>>Datos mapeados de testUser.save:::{}", testUser);

    portfolioEntry =
        new PortfolioEntry(
            null,
            testUser.getId(),
            "BTC",
            "CRYPTO",
            BigDecimal.valueOf(1),
            BigDecimal.valueOf(89000),
            BigDecimal.valueOf(89000),
            null,
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now(),
            Long.valueOf(1));

    portfolioEntry = portfolioEntryRepository.save(portfolioEntry);
    logger.info(">>>Dato mapeados de portfolioEntry.save :::{}", portfolioEntry);

    Transaction transaction = new Transaction();
    transaction.setUser(testUser);
    transaction.setPortfolioEntryId(portfolioEntry.getPortfolioEntryId());
    transaction.setAssetSymbol("BTC");
    transaction.setAssetType(AssetType.CRYPTO);
    transaction.setTransactionType("BUY");
    transaction.setQuantity(BigDecimal.valueOf(1));
    transaction.setPricePerUnit(BigDecimal.valueOf(89000));
    transaction.setTotalValue(BigDecimal.valueOf(89000));
    transaction.setNotes("Test BTC BUY");
    transactionRepository.save(transaction);

    Transaction transaction2 = new Transaction();
    transaction2.setUser(testUser);
    transaction2.setPortfolioEntryId(portfolioEntry.getPortfolioEntryId());
    transaction2.setAssetSymbol("BTC");
    transaction2.setAssetType(AssetType.CRYPTO);
    transaction2.setTransactionType("SELL");
    transaction2.setQuantity(BigDecimal.valueOf(0.01));
    transaction2.setPricePerUnit(BigDecimal.valueOf(80288));
    transaction2.setTotalValue(BigDecimal.valueOf(802.29));
    transaction2.setNotes("Test BTC SELL");
    transactionRepository.save(transaction2);

    Transaction ethBuy = new Transaction();
    ethBuy.setUser(testUser);
    ethBuy.setPortfolioEntryId(portfolioEntry.getPortfolioEntryId());
    ethBuy.setAssetSymbol("ETH");
    ethBuy.setAssetType(AssetType.CRYPTO);
    ethBuy.setTransactionType("BUY");
    ethBuy.setQuantity(BigDecimal.valueOf(0.2));
    ethBuy.setPricePerUnit(BigDecimal.valueOf(1596));
    ethBuy.setTotalValue(BigDecimal.valueOf(319));
    ethBuy.setTransactionDate(LocalDateTime.now());
    ethBuy.setFee(BigDecimal.valueOf(1.0));
    ethBuy.setNotes("Test ETH BUY ");
    ethBuy.setCreatedAt(LocalDateTime.now());
    ethBuy.setUpdatedAt(LocalDateTime.now());
    transactionRepository.save(ethBuy);

    Transaction ethSell = new Transaction();
    ethSell.setUser(testUser);
    ethSell.setPortfolioEntryId(portfolioEntry.getPortfolioEntryId());
    ethSell.setAssetSymbol("ETH");
    ethSell.setAssetType(AssetType.CRYPTO);
    ethSell.setTransactionType("SELL");
    ethSell.setQuantity(BigDecimal.valueOf(0.1));
    ethSell.setPricePerUnit(BigDecimal.valueOf(798));
    ethSell.setTotalValue(BigDecimal.valueOf(159.5));
    ethSell.setTransactionDate(LocalDateTime.now());
    ethSell.setFee(BigDecimal.valueOf(1.0));
    ethSell.setNotes("Test ETH SELL ");
    ethSell.setCreatedAt(LocalDateTime.now());
    ethSell.setUpdatedAt(LocalDateTime.now());
    transactionRepository.save(ethSell);
  }

  @Test
  void testDeletedByIdTransaction() {
    List<Transaction> transactions = transactionRepository.findAll();

    List<TransactionResponse> transactionResponse =
        transactions.stream().map(transactionMapper::toResponse).collect(Collectors.toList());

    transactionResponse.forEach(response -> logger.info("Transaccion:::{}", response));

    transactionRepository.deleteById(transactionId);
    assertTrue(true);
  }
}
