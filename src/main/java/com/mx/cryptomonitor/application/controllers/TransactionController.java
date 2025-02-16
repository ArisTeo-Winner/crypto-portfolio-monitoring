package com.mx.cryptomonitor.application.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.application.mappers.TransactionMapper;
import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.services.TransactionService;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;
import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;

@RestController
@RequestMapping("/api/transactions")
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
    public ResponseEntity<List<TransactionResponse>> getTransactionsByUserAndSymbol(
            @PathVariable UUID userId,
            @PathVariable String assetSymbol) {
        return ResponseEntity.ok(transactionService.getTransactionsByUserAndSymbol(userId, assetSymbol));
    }

}
