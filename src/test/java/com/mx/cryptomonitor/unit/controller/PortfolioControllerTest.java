package com.mx.cryptomonitor.unit.controller;


import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.application.controllers.PortfolioController;
import com.mx.cryptomonitor.domain.services.PortfolioService;
import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {
	
    private static final Logger logger = LoggerFactory.getLogger(PortfolioController.class);


    private PortfolioService portfolioService;
    private PortfolioController portfolioController;

    @BeforeEach
    public void setup() {
        // Se crea un mock del PortfolioService y se inyecta en el controlador.
        portfolioService = Mockito.mock(PortfolioService.class);
        portfolioController = new PortfolioController(portfolioService);
    }

    @Test
    public void testRegisterTransaction_Success() {
    	
    	logger.info("=== Ejecutando método testRegisterTransaction_Success() desde PortfolioControllerTest ===");
    	
        UUID userId = UUID.randomUUID();
        UUID portfolio_entry_id = UUID.randomUUID();

        TransactionRequest request = new TransactionRequest(
            userId,
            portfolio_entry_id,
            "BTC",
            "CRYPTO",
            "BUY",
            BigDecimal.valueOf(1),
            BigDecimal.valueOf(50000),  // princePerUnit
            BigDecimal.valueOf(50000),
            LocalDateTime.now(),
            BigDecimal.ZERO,
            BigDecimal.valueOf(50000),
            "Compra de BTC"
        );

        TransactionResponse expectedResponse = new TransactionResponse(
        	userId,
            "BTC",
            "CRYPTO",
            "BUY",
            BigDecimal.valueOf(1),
            BigDecimal.valueOf(50000),
            BigDecimal.valueOf(50000),
            LocalDateTime.now(),
            BigDecimal.ZERO,
            BigDecimal.valueOf(50000),
            "Compra de BTC",
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        // Se simula que el servicio retorna la respuesta esperada.
        when(portfolioService.registerTransaction(any(TransactionRequest.class))).thenReturn(expectedResponse);

        var responseEntity = portfolioController.createTransaction(request);
        
    	logger.info("responseEntity = "+responseEntity);

        assertEquals(201, responseEntity.getStatusCodeValue());
        assertEquals(expectedResponse, responseEntity.getBody());
    }
    /*
    @Test
    public void testGetTransactionsByUser_Success() {
    	
    	UUID userdId = UUID.randomUUID();
    	TransactionResponse transactionResponse = new TransactionResponse(
    			UUID.randomUUID(), 
    			userdId, 
    			"ETH", 
    			"CRYPTO", 
    			"BUY", 
    			BigDecimal.valueOf(10), 
    			BigDecimal.valueOf(2000), 
    			BigDecimal.valueOf(20000), 
    			LocalDateTime.now(), 
    			BigDecimal.ZERO, 
    			BigDecimal.valueOf(2000), 
    			"Compra de ETH", 
    			LocalDateTime.now(), 
    			LocalDateTime.now());
    	
    	List<TransactionResponse> expectedList = Collections.singletonList(transactionResponse);
    	when(portfolioService.getTransactionsByUser(userdId)).thenReturn(expectedList);
    	
    	var responseEntity = portfolioController.getTransactionsByUser(userdId);
    	assertEquals(200, responseEntity.getStatusCodeValue());
    	assertEquals(expectedList, responseEntity.getBody());
    }
    
    @Test
    @Disabled
    public void testRegisterTransaction_Exception() {
        // Simula que el servicio arroja una excepción.
        TransactionRequest request = new TransactionRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BTC",
            "CRYPTO",
            "SELL",
            BigDecimal.valueOf(2),
            BigDecimal.valueOf(55000),
            BigDecimal.valueOf(110000),
            LocalDateTime.now(),
            BigDecimal.ZERO,
            BigDecimal.valueOf(55000),
            "Venta de BTC"
        );
        when(portfolioService.registerTransaction(any(TransactionRequest.class)))
            .thenThrow(new RuntimeException("Test Exception"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            portfolioController.createTransaction(request);
        });
        assertEquals("Test Exception", exception.getMessage());
    }

    */
}
