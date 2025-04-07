package com.mx.cryptomonitor.application.controllers;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.services.PortfolioService;
import com.mx.cryptomonitor.domain.services.TransactionService;
import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {
	
    private static final Logger logger = LoggerFactory.getLogger(PortfolioController.class);
	
	@Autowired
	private TransactionService transactionService;
	
	@Autowired
	private PortfolioService portfolioService;
	

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

  
	/**
	 * Endpoint para registrar transacciones.
	 * Ejemplo de uso: POST /api/v1/portfolio/transactions?assetSymbol=ETH ...
	 * 
	 * @param symbol Símbolo de activo (no nulo ni vacío)
	 * @return ResponseEntity con el precio de cierre o un mensaje de error
	 * 
	 * */
    
    @PostMapping("/transactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody TransactionRequest request) {
    	
        logger.info("📌 Recibida nueva transacción: {}", request);
        TransactionResponse response = portfolioService.registerTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);			
	

    }
    
    @GetMapping("/transactions/{userId}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByUser(@PathVariable UUID userdId){
    	
    	try {
    		List<TransactionResponse> responses = portfolioService.getTransactionsByUser(userdId);
			return ResponseEntity.ok(responses);
		} catch (Exception e) {
			// TODO: handle exception
			logger.error("Error en getTransactionsByUser del controlador: {}", e.getMessage(), e);
			throw e;
		}
    }
    
	/**
	 * Endpoint listar transacciones por usuario.
	 * Ejemplo de uso: POST /api/v1/portfolio/transactions/user
	 * 
	 * */
    
    @GetMapping("/transactions/user")
    public List<TransactionResponse> getUserTransactions(){
    	
    	logger.info(">>>getUserTransactions>>>");
    	
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    	
    	UUID userId = (UUID) authentication.getPrincipal();
 
		return portfolioService.getTransactionsByUser(userId);
    	
    }
    
	
}
