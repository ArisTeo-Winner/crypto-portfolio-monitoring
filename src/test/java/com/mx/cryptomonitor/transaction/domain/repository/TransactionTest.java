package com.mx.cryptomonitor.transaction.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionResponse;
import com.mx.cryptomonitor.transaction.application.mapper.TransactionMapper;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
class TransactionTest {

  private static final Sort RECENT_FIRST_SORT =
      Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("createdAt"));

  private final Logger logger = LoggerFactory.getLogger(TransactionTest.class);

  @Autowired private PortfolioEntryRepository portfolioEntryRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TestEntityManager entityManager;
  @Autowired private EntityManager em;

  private final TransactionMapper transactionMapper = Mappers.getMapper(TransactionMapper.class);

  private PortfolioEntry portfolioEntry;
  private User testUser;

  @BeforeEach
  void setUp() {
    testUser =
        User.builder()
            .username("leo")
            .email("leo@example.com")
            .passwordHash("hashed_password")
            .firstName("manzano")
            .build();

    testUser = entityManager.persistFlushFind(testUser);

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
            null);

    entityManager.persist(portfolioEntry);
    entityManager.flush();

    Transaction tx =
        Transaction.builder()
            .user(testUser)
            .assetSymbol("BTC")
            .assetType(AssetType.CRYPTO)
            .transactionType("BUY")
            .quantity(new BigDecimal("1"))
            .pricePerUnit(new BigDecimal("100"))
            .totalValue(new BigDecimal("100"))
            .transactionDate(OffsetDateTime.now(ZoneOffset.UTC))
            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
            .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
            .portfolioEntryId(portfolioEntry.getPortfolioEntryId())
            .build();

    transactionRepository.saveAndFlush(tx);
    em.clear();

    var list = transactionRepository.findByUserId(testUser.getId(), RECENT_FIRST_SORT);
    assertThat(list).hasSize(1);
  }

  @Test
  void testFindTransactionByUser() {
    List<Transaction> transaction =
        transactionRepository.findByUserId(testUser.getId(), RECENT_FIRST_SORT);

    for (Transaction transactiones : transaction) {
      logger.info(transactiones.toString());
    }

    assertThat(transaction).isNotEmpty();
  }

  @Test
  void testFindTransactionByUserIdAndAssetSymbol() {
    List<Transaction> transaction =
        transactionRepository.findByUserIdAndAssetSymbol(
            testUser.getId(), "BTC", RECENT_FIRST_SORT);

    assertThat(transaction).isNotEmpty();
    assertEquals("BTC", transaction.get(0).getAssetSymbol());
  }

  @Test
  void testFindTransactionByUserIdAndAssetType() {
    List<Transaction> transaction =
        transactionRepository.findByUserIdAndAssetType(
            testUser.getId(), AssetType.CRYPTO, RECENT_FIRST_SORT);

    assertNotNull(transaction);
    assertEquals(AssetType.CRYPTO, transaction.get(0).getAssetType());
  }

  @Test
  void testFindTransactionByIdAndTransactionType() {
    List<Transaction> transaction =
        transactionRepository.findByUserIdAndTransactionType(
            testUser.getId(), "BUY", RECENT_FIRST_SORT);

    assertThat(transaction).isNotEmpty();
    assertEquals("BUY", transaction.get(0).getTransactionType());
  }

  @Test
  void testFindTransactionByUserAndSymbolAnsTypeAndtransactionType() {
    logger.info(
        ">>> TransactionTest >>> testFindTransactionByUserAndSymbolAnsTypeAndtransactionType ");

    List<Transaction> transaction =
        transactionRepository.findByUserIdAndAssetSymbolAndAssetTypeAndTransactionType(
            testUser.getId(), "BTC", AssetType.CRYPTO, "BUY");

    logger.info(
        "Consulta de List<Transaction> transactionRepository.findByUserIdAndAssetSymbolAndAssetTypeAndTransactionType(): {}",
        transaction);

    var responseList =
        transaction.stream()
            .filter(Objects::nonNull)
            .map(transactionMapper::toResponse)
            .collect(Collectors.toList());

    logger.info("Consulta de List<TransactionResponse> responseList(): {}", responseList);

    for (Iterator iterator = responseList.iterator(); iterator.hasNext(); ) {
      TransactionResponse transactionResponse = (TransactionResponse) iterator.next();
      logger.info("Transaccion:::{}", transactionResponse);
    }

    assertThat(responseList).isNotEmpty();
    assertEquals("BTC", responseList.get(0).assetSymbol());
    assertEquals("CRYPTO", responseList.get(0).assetType());
    assertEquals("BUY", responseList.get(0).transactionType());
  }
}
