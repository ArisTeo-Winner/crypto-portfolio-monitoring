package com.mx.cryptomonitor.application.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.application.mappers.TransactionMapper;
import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.services.TransactionService;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;

import lombok.extern.slf4j.Slf4j;

import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    
    @Autowired
    private TransactionMapper transactionMapper; 

    public TransactionController(TransactionService transactionService, TransactionMapper transactionMapper) {
        this.transactionService = transactionService;
        this.transactionMapper = transactionMapper;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@RequestBody TransactionRequest transactionRequest) {
    	TransactionResponse savedTransaction = transactionService.saveTransaction(transactionRequest);
        return ResponseEntity.ok(savedTransaction);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(transactionService.getTransactionsByUser(userId));
    }

    @GetMapping("/{userId}/{assetSymbol}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByUserAndSymbol(
            @PathVariable UUID userId,
            @PathVariable String assetSymbol) {
        return ResponseEntity.ok(transactionService.getTransactionsByUserAndSymbol(userId, assetSymbol));
    }
    
	/**
	 * Endpoint listar transacciones por usuario.
	 * Ejemplo de uso: POST /api/v1/portfolio/transactions/list
	 *
    @GetMapping("/list")
    public List<TransactionResponse> getUserTransactions(){
    	
    	log.info(">>>getUserTransactions>>>");
    	
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    	
    	UUID userId = (UUID) authentication.getPrincipal();
 
		return transactionService.getTransactionsByUser(userId);
    } */
    
    @GetMapping("/list")
    public ResponseEntity<List<TransactionResponse>> getUserTransactions(
            @RequestParam(value = "assetSymbol", required = false) String assetSymbol,
            @RequestParam(value = "assetType", required = false) String assetType,
            @RequestParam(value = "transactionType", required = false) String transactionType) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = (UUID) authentication.getPrincipal();
        
        log.info("userId:{},assetSymbol:{}, assetType:{}, transactionType:{}",userId,assetSymbol,assetType,transactionType);

        List<TransactionResponse> transactions = transactionService.getTransactionsUser(userId, assetSymbol, assetType, transactionType);
        log.info("Respuesta: {}",transactions);
        return ResponseEntity.ok(transactions);
    }
    

}
