package com.mx.cryptomonitor.integration;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.integration.support.InfraIntegrationTest;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@DataJpaTest
@ActiveProfiles("test")
@Rollback(false)
class PortfolioServiceIntegrationTest extends InfraIntegrationTest {

  private final Logger logger = LoggerFactory.getLogger(PortfolioServiceIntegrationTest.class);

  @Autowired private TransactionRepository transactionRepository;
  @Autowired private PortfolioEntryRepository portfolioEntryRepository;
  @Autowired private UserRepository userRepository;

  private User user;
  private UUID userId;

  @BeforeEach
  public void setUp() {
    user = new User();
    user.setUsername("testUser");
    user.setEmail("testuser@example.com");
    user.setPasswordHash("hashedpassword");
    userRepository.save(user);
  }

  @Test
  void testSaveTransactionAndPortfolioEntry() {
    userId = user.getId();
    User persistedUser =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

    logger.info("Cosulta un UUID de user: {}", persistedUser);

    PortfolioEntry portfolioEntry =
        PortfolioEntry.builder()
            .userId(persistedUser.getId())
            .assetSymbol("ETH")
            .assetType("CRYPTO")
            .totalQuantity(BigDecimal.valueOf(2.0))
            .totalInvested(BigDecimal.valueOf(5000))
            .averagePricePerUnit(BigDecimal.valueOf(2500))
            .lastTransactionPrice(null)
            .currentValue(null)
            .totalProfitLoss(null)
            .lastUpdated(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    portfolioEntry = portfolioEntryRepository.save(portfolioEntry);

    assertNotEquals(portfolioEntry.getUserId(), null, "El userId en PortfolioEntry es NULL");
    logger.info("ID generado por JPA: {}", portfolioEntry.getUserId());

    Transaction transaction =
        Transaction.builder()
            .user(persistedUser)
            .portfolioEntryId(portfolioEntry.getPortfolioEntryId())
            .assetSymbol("ETH")
            .assetType(AssetType.CRYPTO)
            .transactionType("BUY")
            .quantity(BigDecimal.valueOf(2.0))
            .pricePerUnit(BigDecimal.valueOf(2500))
            .totalValue(BigDecimal.valueOf(5000))
            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
            .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
            .build();

    logger.info("List transaction :{}", transaction);

    assertNotNull(transaction.getUser(), "ERROR: El usuario en Transaction es NULL");
    assertNotNull(transaction.getUser().getId(), "ERROR: El ID del usuario en Transaction es NULL");
    assertNotNull(
        transaction.getPortfolioEntryId(), "ERROR: El portfolioEntryId en Transaction es NULL");

    transactionRepository.save(transaction);

    assertNotNull(transactionRepository.findById(transaction.getTransactionId()));
    assertNotNull(
        portfolioEntryRepository.findByUserIdAndAssetSymbol(portfolioEntry.getUserId(), "ETH"));
  }

  @Test
  void testByIdUserTransaction() {}
}
