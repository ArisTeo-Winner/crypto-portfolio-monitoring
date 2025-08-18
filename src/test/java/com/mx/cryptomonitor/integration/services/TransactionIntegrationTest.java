
package com.mx.cryptomonitor.integration.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.application.mappers.TransactionMapper;
import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.PortfolioEntryRepository;
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.TransactionService;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
class TransactionIntegrationTest {
	
	private Logger logger = LoggerFactory.getLogger(TransactionIntegrationTest.class);
	
	@Autowired
	private TransactionRepository transactionRepository;
	
	@Autowired
	private  UserRepository userRepository;
	
	@Autowired
	private PortfolioEntryRepository portfolioEntryRepository;
	
	@Autowired
	private TransactionMapper transactionMapper;
	
	@Autowired
	private ObjectMapper objectMapper; 

	
	@Autowired
	private TransactionService transactionService;

	private User testUser;
	private UUID userId;
	
	private UUID portfolioEntryId;

	private PortfolioEntry portfolioEntry;

	private UUID transactionId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		
		testUser = User.builder()
				.username("leo")
				.email("leo@example.com")
				.passwordHash("hashed_password")
				.firstName("manzano")
				.build();
		
		
		testUser = userRepository.save(testUser);
		
		logger.info(">>>Datos mapeados de testUser.save:::"+testUser);
		
		portfolioEntry = new PortfolioEntry(
				portfolioEntryId.randomUUID(), 
				testUser, 
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
		
		 logger.info(">>>Dato mapeados de portfolioEntry.save :::"+portfolioEntry);
		
		
		/**/
		Transaction transaction = new Transaction(); 
		transaction.setTransactionId(transactionId);
		transaction.setUser(testUser);
		transaction.setPortfolioEntry(portfolioEntry);
		transaction.setAssetSymbol("BTC");
		transaction.setAssetType("CRYPTO");
		transaction.setTransactionType("BUY");
		transaction.setQuantity(BigDecimal.valueOf(1));
		transaction.setPricePerUnit(BigDecimal.valueOf(89000));
		transaction.setTotalValue(BigDecimal.valueOf(89000));
		transaction.setNotes("Test BTC BUY");
		
		logger.info(">>>Dato mapeados de transaction.save :::"+transaction);

		
		transactionRepository.save(transaction);

		
		Transaction transaction2 = new Transaction(); 
		transaction2.setUser(testUser);
		transaction2.setPortfolioEntry(portfolioEntry);
		transaction2.setAssetSymbol("BTC");
		transaction2.setAssetType("CRYPTO");
		transaction2.setTransactionType("SELL");
		transaction2.setQuantity(BigDecimal.valueOf(0.01));
		transaction2.setPricePerUnit(BigDecimal.valueOf(80288));
		transaction2.setTotalValue(BigDecimal.valueOf(802.29));
		transaction2.setNotes("Test BTC SELL");
		
		transactionRepository.save(transaction2);
		
		transactionRepository.save(new Transaction(
				UUID.randomUUID(), 
				testUser, 
				portfolioEntry, 
				"ETH", 
				"CRYPTO", 
				"BUY", 
				BigDecimal.valueOf(0.2), 
				BigDecimal.valueOf(1596), 
				BigDecimal.valueOf(319), 
				LocalDateTime.now(), 
				BigDecimal.valueOf(1.0), 
				"Test ETH BUY ", 
				LocalDateTime.now(), 
				LocalDateTime.now()));
		
		transactionRepository.save(new Transaction(
				UUID.randomUUID(), 
				testUser, 
				portfolioEntry, 
				"ETH", 
				"CRYPTO", 
				"SELL", 
				BigDecimal.valueOf(0.1), 
				BigDecimal.valueOf(798), 
				BigDecimal.valueOf(159.5), 
				LocalDateTime.now(), 
				BigDecimal.valueOf(1.0), 
				"Test ETH SELL ", 
				LocalDateTime.now(), 
				LocalDateTime.now()));
		
		}
	
	@Test
	void testDeletedByIdTransaction() {
		
		List<Transaction> transaction = transactionRepository.findAll();
		
		List<TransactionResponse> transactionResponse =  transaction.stream()
				.map(transactionMapper::toResponse)
				.collect(Collectors.toList());
		
		//logger.info("testListTransaction");
		
		for (Iterator iterator = transactionResponse.iterator(); iterator.hasNext();) {
			TransactionResponse transactionResponse2 = (TransactionResponse) iterator.next();
			logger.info(transactionResponse2.toString());
		}
		
		
		transactionRepository.deleteById(transactionId);
		
		//verify(transactionRepository,times(1)).deleteById(transactionId);
		
	}

}
