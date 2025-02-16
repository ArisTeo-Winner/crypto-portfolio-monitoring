package com.mx.cryptomonitor.unit.service;

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
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
import com.mx.cryptomonitor.domain.services.TransactionService;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;
import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;
import com.mx.cryptomonitor.application.mappers.TransactionMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionService transactionService;

    private Transaction transaction;
    private TransactionRequest request;
    private TransactionResponse response;
    private UUID userId;
    private UUID portfolio_entry_id;
/*

    @BeforeEach
    public void setUp() {
        userId = UUID.randomUUID();
        portfolio_entry_id = UUID.randomUUID();

        request = new TransactionRequest(
            userId, 
            portfolio_entry_id,
            "BTC", 
            "CRYPTO", 
            "BUY", 
            new BigDecimal("1.5"), 
            new BigDecimal("50000.00"), 
            new BigDecimal("75000.00"), 
            null, 
            new BigDecimal("10.00"), 
            null, 
            "Test transaction"
        );

        transaction = new Transaction();
        transaction.setTransactionId(UUID.randomUUID());
        transaction.setUserId(userId);
        transaction.setAssetSymbol("BTC");
        transaction.setAssetType("CRYPTO");
        transaction.setTransactionType("BUY");
        transaction.setQuantity(new BigDecimal("1.5"));
        transaction.setPricePerUnit(new BigDecimal("50000.00"));
        transaction.setTotalValue(new BigDecimal("75000.00"));

        response = new TransactionResponse(
            transaction.getTransactionId(), 
            userId, 
            "BTC", 
            "CRYPTO", 
            "BUY", 
            new BigDecimal("1.5"), 
            new BigDecimal("50000.00"), 
            new BigDecimal("75000.00"), 
            transaction.getTransactionDate(), 
            new BigDecimal("10.00"), 
            null, 
            "Test transaction", 
            null, 
            null
        );
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
        assertEquals(response.transactionId(), result.transactionId());
        verify(transactionMapper, times(1)).toEntity(request);
        verify(transactionRepository, times(1)).save(transaction);
        verify(transactionMapper, times(1)).toResponse(transaction);
    }




    @Test
    public void testGetTransactionsByUser() {
    	
    	TransactionResponse transactionResponse = new TransactionResponse(
    		    transaction.getTransactionId(),
    		    transaction.getUserId(),
    		    transaction.getAssetSymbol(),
    		    transaction.getAssetType(),
    		    transaction.getTransactionType(),
    		    transaction.getQuantity(),
    		    transaction.getPricePerUnit(),
    		    transaction.getTotalValue(),
    		    transaction.getTransactionDate(),
    		    transaction.getFee(),
    		    transaction.getPriceAtTransaction(),
    		    transaction.getNotes(),
    		    transaction.getCreatedAt(),
    		    transaction.getUpdatedAt()
    		);

    	

    	
        // Configurar comportamiento simulado
    	when(transactionRepository.findByUserId(userId)).thenReturn(Arrays.asList(transactionResponse));

        // Ejecutar el método a probar
        List<TransactionResponse> transactions = transactionService.getTransactionsByUser(userId);

        // Verificar resultados
        assertEquals(1, transactions.size());
        assertEquals(transaction.getAssetSymbol(), transactions.get(0).assetSymbol());
        verify(transactionRepository, times(1)).findByUserId(userId);
    }*/
}
