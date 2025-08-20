package com.mx.cryptomonitor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
import com.mx.cryptomonitor.domain.services.TransactionService;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;
import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;
import com.mx.cryptomonitor.application.controllers.PortfolioController;
import com.mx.cryptomonitor.application.mappers.TransactionMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {
	
	private static final Logger logger = LoggerFactory.getLogger(PortfolioController.class);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionService transactionService;

    private User user;
    private Transaction transaction;
    private TransactionRequest request;
    private TransactionResponse response;
    
    private UUID userId;
    private UUID portfolio_entry_id;
    private UUID transactionId;
    
    private String assetSymbol;

    @BeforeEach
    public void setUp() {
        userId = UUID.randomUUID();
        portfolio_entry_id = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        assetSymbol = "BTC";

        
        request = new TransactionRequest(        		
        		portfolio_entry_id, 
        		"BTC", 
        		"CRYTO", 
        		"BUY", 
        		BigDecimal.valueOf(1.5), 
        		BigDecimal.valueOf(45000), 
        		BigDecimal.valueOf(67500), 
        		null, 
        		null, 
        		null
        		);
        
        user = User.builder()
        		.id(userId)
        		.username("testuser")
        		.email("testuser@example.com")
        		.passwordHash("hashedpassword")
        		.firstName("test")
        		.roles(new ArrayList<>())
        		.build();
        
        logger.info("Lista de user :{}",user);


        transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setUser(user);
        transaction.setAssetSymbol("BTC");
        transaction.setAssetType("CRYPTO");
        transaction.setTransactionType("BUY");
        transaction.setQuantity(new BigDecimal("1.5"));
        transaction.setPricePerUnit(new BigDecimal("50000.00"));
        transaction.setTotalValue(new BigDecimal("75000.00"));

        response = new TransactionResponse(
        		user.getId(),  
        		"BTC", 
        		"CRYTO", 
        		"BUY", 
        		BigDecimal.valueOf(1.5), 
        		BigDecimal.valueOf(45000), 
        		BigDecimal.valueOf(67500), 
        		null, 
        		null, 
        		null, 
        		null,
        		null);
    }

    @Test
    public void testCreateTransaction() {
        // Configurar comportamiento simulado
        when(transactionMapper.toEntity(request)).thenReturn(transaction);
        when(transactionRepository.save(transaction)).thenReturn(transaction);
        when(transactionMapper.toResponse(transaction)).thenReturn(response);

        // Ejecutar el método a probar
        TransactionResponse result = transactionService.saveTransaction(request);

        // Verificar resultados
        assertEquals(response.userId(), result.userId());
        verify(transactionMapper, times(1)).toEntity(request);
        verify(transactionRepository, times(1)).save(transaction);
        verify(transactionMapper, times(1)).toResponse(transaction);
    }

/**/

    @Test
    public void testGetTransactionsByUser() {
    	/*
    	TransactionResponse transactionResponse = new TransactionResponse(
    		    transaction.getTransactionId(),
    		    //transaction.getUser().getId(),
    		    transaction.getAssetSymbol(),
    		    transaction.getAssetType(),
    		    transaction.getTransactionType(),
    		    transaction.getQuantity(),
    		    transaction.getPricePerUnit(),
    		    transaction.getTotalValue(),
    		    transaction.getTransactionDate(),
    		    transaction.getFee(),
    		    transaction.getNotes(),
    		    transaction.getCreatedAt(),
    		    transaction.getUpdatedAt()
    		);*/

    	//when(transactionRepository.findByUserId(userId)).thenReturn(List.of());

    	assertEquals(transaction.getUser().getId(), user.getId());
        // Configurar comportamiento simulado
    	when(transactionRepository.findByUserId(userId)).thenReturn(Arrays.asList(response));

        // Ejecutar el método a probar
        List<TransactionResponse> transactions = transactionService.getTransactionsByUser(userId);

        // Verificar resultados
        assertEquals(1, transactions.size());
        assertEquals(transaction.getAssetSymbol(), transactions.get(0).assetSymbol());
        verify(transactionRepository, times(1)).findByUserId(userId);
    }
    
    @Test
    void testGetTransactionsByUserAndSymbol() {
    	when(transactionRepository.findByUserIdAndAssetSymbol(userId, assetSymbol)).
    		thenReturn(List.of(response));
    	
    	List<TransactionResponse> result = transactionService.getTransactionsByUserAndSymbol(userId, assetSymbol);
    	
    	System.out.println("Respuesta de .getTransactionsByUserAndSymbol(userId, assetSymbol)"+result);
    	
    	assertNotNull(result);
    	assertFalse(result.isEmpty());
    	assertEquals(1, result.size());
    	assertEquals(assetSymbol, result.get(0).assetSymbol());
    	assertEquals("BUY", result.get(0).transactionType());
    	
    	verify(transactionRepository, times(1)).findByUserIdAndAssetSymbol(userId, assetSymbol);
    	
    	
    }
    
    /*
    @Test
    public void whenGetTransactionsUser_thenReturnTransactionResponses() {

        when(transactionRepository.findByUserIdAndAssetSymbolAndAssetTypeAndTransactionType(userId, "ETH", "CRYPTO", "BUY"))
            .thenReturn(Collections.singletonList(new Transaction(transactionId, user, null, "ETH", "CRYPTO", "BUY", null, null, null, null, null, assetSymbol, null, null)));

        // Ejecutar el método del servicio
        List<TransactionResponse> transactions = transactionService.getTransactionsUser(userId, "ETH", "CRYPTO", "BUY");
    	System.out.println("Respuesta de .getTransactionsUser(userId, \"ETH\", \"CRYPTO\", \"BUY\")"+transactions);

        logger.info("Consulta de transactionService.getTransactionsUser(userId, \"ETH\", \"CRYPTO\", \"BUY\"): {}",transactions);
        
        // Verificar el resultado
        assertThat(transactions).isNotEmpty();
        assertEquals("BUY", transactions.get(0).transactionType());
        //assertThat(transactions.get(0).transactionType()).isEqualTo("BUY");
    }
    */
}
