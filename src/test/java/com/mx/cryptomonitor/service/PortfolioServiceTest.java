package com.mx.cryptomonitor.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.h2.command.dml.MergeUsing.When;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mx.cryptomonitor.application.mappers.TransactionMapper;
import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.PortfolioEntryRepository;
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.PortfolioService;
import com.mx.cryptomonitor.infrastructure.api.MarketDataService;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;
import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;


import java.math.BigDecimal;
import java.sql.Time;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    private static final Logger logger = (Logger) LoggerFactory.getLogger(PortfolioServiceTest.class);

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PortfolioEntryRepository portfolioEntryRepository;

    @Mock
    private TransactionMapper transactionMapper;
    
    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private PortfolioService portfolioService;

    private UUID userId;
    private UUID portfolio_entry_id;
    private TransactionRequest request;
    private Transaction transaction;
    private TransactionResponse response;
    private PortfolioEntry portfolioEntry;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        portfolio_entry_id = UUID.randomUUID();

        
        user = User.builder()
        		.id(userId)
        		.username("testuser")
        		.email("testuser@gmail.com")
        		.passwordHash("password123")
        		.build();
        
        //userRepository.save(user);
       
        logger.info("Respuesta userRepository.save:{}",user);
        
        request = new TransactionRequest(
        		user.getId(), 
        		portfolio_entry_id, 
        		"BTC", 
        		"CRYPTO", 
        		"BUY", 
        		new BigDecimal("1.5"), 
        		new BigDecimal("50000.00"), 
        		new BigDecimal("75000.00"), 
        		null, 
        		new BigDecimal("0.5"), 
        		null);
        
        logger.info("Respuesta TransactionResponse():{}",response);

        portfolioEntry = new PortfolioEntry(
        	portfolio_entry_id, 
            user, 
            "BTC", 
            "CRYPTO",
            new BigDecimal("2.0"), 
            new BigDecimal("100000.00"), 
            new BigDecimal("50000.00"), 
            new BigDecimal("50000.00"), 
            new BigDecimal("50000.00"), 
            new BigDecimal("50000.00"), 
            null, 
            null, 
            null
        );
        
        transaction = new Transaction();
        transaction.setTransactionId(UUID.randomUUID());
        transaction.setUser(user);
        transaction.setPortfolioEntry(portfolioEntry);
        transaction.setAssetSymbol("BTC");
        transaction.setAssetType("CRYPTO");
        transaction.setTransactionType("BUY");
        transaction.setQuantity(new BigDecimal("1.5"));
        transaction.setPricePerUnit(new BigDecimal("50000.00"));
        transaction.setTotalValue(new BigDecimal("75000.00"));

        logger.info("Respuesta Transaction():{}",transaction);

        response = new TransactionResponse(
        		user.getId(), "BTC", "CRYPTO", "BUY", 
            new BigDecimal("1.5"), new BigDecimal("50000.00"), 
            new BigDecimal("75000.00"), transaction.getTransactionDate(), 
            new BigDecimal("10.00"), null, null, null
        );        

    }

    @Test
    void testRegisterTransaction_Buy() {
    	
    	logger.info("=== Ejecutando método testRegisterTransaction_Buy() ===");

    	
        assertNotNull(user.getId(), "El ID del usuario no debería ser null");
        
        logger.info("El ID del usuario: {}", user.getId());
        logger.info("Respuesta TransactionRequest():{}",request);
        logger.info("Respuesta portfolioEntry():{}",portfolioEntry);
        
        logger.debug("🔍 Buscando portfolioEntry para userId: {}, assetSymbol: {}", userId, "BTC");
        Optional<PortfolioEntry> existingEntry = portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "BTC");
        logger.debug("🔎 ¿PortfolioEntry encontrado?: {}", existingEntry.isPresent());

        when(marketDataService.getCryptoPrice("BTC"))
        .thenReturn(Optional.of(new BigDecimal("50000.00")));  // Simular precio de BTC

        when(transactionMapper.toEntity(request)).thenReturn(transaction);
        when(transactionRepository.save(transaction)).thenReturn(transaction);
        when(portfolioEntryRepository.findByUserIdAndAssetSymbol(any(UUID.class), anyString()))
                .thenReturn(Optional.of(portfolioEntry));
        when(transactionMapper.toResponse(transaction)).thenReturn(response);
        when(userRepository.findById(any(UUID.class)))
        .thenReturn(Optional.of(user));
        
        TransactionResponse result = portfolioService.registerTransaction(request);
        
        assertNotNull(result);
        assertEquals("BTC", result.assetSymbol());
        assertEquals("BUY", result.transactionType());
        verify(transactionRepository, times(1)).save(transaction);
        verify(portfolioEntryRepository, times(1)).save(any(PortfolioEntry.class));
    }
/*
    @Test
    void testRegisterTransaction_Sell() {
    	TransactionRequest request_Sell = new TransactionRequest(
            userId, portfolio_entry_id, "BTC", "CRYPTO", "SELL", new BigDecimal("1.0"), 
            new BigDecimal("50000.00"), new BigDecimal("50000.00"), 
            null, new BigDecimal("10.00"), null, "Venta BTC"
        );
    	
        // 📌 Crear una nueva transacción para esta prueba con "SELL"
        Transaction sellTransaction = Transaction.builder()
                .transactionId(UUID.randomUUID())
                .userId(userId)
                .assetSymbol("BTC")
                .assetType("CRYPTO")
                .transactionType("SELL") // 🔥 Asegurar que es "SELL"
                .quantity(BigDecimal.valueOf(1.0))
                .pricePerUnit(BigDecimal.valueOf(50000.00))
                .totalValue(BigDecimal.valueOf(50000.00))
                .notes("Venta BTC")
                .build();
        
        logger.info("request ::: "+request_Sell);

        portfolioEntry.setTotalQuantity(new BigDecimal("2.0"));
        portfolioEntry.setTotalInvested(new BigDecimal("100000.00"));

        when(transactionMapper.toEntity(request_Sell)).thenReturn(sellTransaction);
        when(transactionRepository.save(sellTransaction)).thenReturn(sellTransaction);
        when(portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "BTC"))
                .thenReturn(Optional.of(portfolioEntry));
        
        // 📌 Modificación aquí: el tipo de transacción en la respuesta se asigna correctamente
        TransactionResponse response_Sell = new TransactionResponse(
        		sellTransaction.getTransactionId(), userId, "BTC", "CRYPTO", "SELL", // 🔥 "SELL" en lugar de "BUY"
            new BigDecimal("1.0"), new BigDecimal("50000.00"), 
            new BigDecimal("50000.00"), sellTransaction.getTransactionDate(), 
            new BigDecimal("10.00"), null, "Venta BTC", null, null
        );
        
        logger.info("response ::: "+response_Sell);

        
        when(transactionMapper.toResponse(sellTransaction)).thenReturn(response_Sell);

        TransactionResponse result = portfolioService.registerTransaction(request_Sell);
        
        logger.info("result ::: "+result);

        assertNotNull(result);
        assertEquals("BTC", result.assetSymbol());
        assertEquals("SELL", result.transactionType());
        verify(transactionRepository, times(1)).save(sellTransaction);
        verify(portfolioEntryRepository, times(1)).save(any(PortfolioEntry.class));
    }

    */	
    
    
/*
    @Test
    void testRegisterTransaction_BuyTransaction_NewEntry() {
        when(portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "BTC")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(buyTransaction);
        when(portfolioEntryRepository.save(any(PortfolioEntry.class))).thenReturn(portfolioEntry);

        TransactionResponse savedTransaction = portfolioService.registerTransaction(transactionRequest);

        assertNotNull(savedTransaction);
        assertEquals("BTC", savedTransaction.assetSymbol());
        verify(transactionRepository, times(1)).save(buyTransaction);
        verify(portfolioEntryRepository, times(1)).save(any(PortfolioEntry.class));
    }

    @Test
    void testRegisterTransaction_BuyTransaction_ExistingEntry() {
        portfolioEntry.setTotalQuantity(BigDecimal.valueOf(1.0));
        portfolioEntry.setTotalInvested(BigDecimal.valueOf(20000));
        portfolioEntry.setAveragePricePerUnit(BigDecimal.valueOf(20000));

        when(portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "BTC")).thenReturn(Optional.of(portfolioEntry));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(buyTransaction);

        TransactionResponse savedTransaction = portfolioService.registerTransaction(buyTransaction);

        assertNotNull(savedTransaction);
        assertEquals("BTC", savedTransaction.getAssetSymbol());
        assertEquals(BigDecimal.valueOf(1.5), portfolioEntry.getTotalQuantity());
        assertEquals(BigDecimal.valueOf(30000), portfolioEntry.getTotalInvested());
        verify(transactionRepository, times(1)).save(buyTransaction);
        verify(portfolioEntryRepository, times(1)).save(any(PortfolioEntry.class));
    }*/
}
