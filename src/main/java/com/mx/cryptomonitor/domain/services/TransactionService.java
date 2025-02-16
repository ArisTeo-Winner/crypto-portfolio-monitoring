package com.mx.cryptomonitor.domain.services;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.application.mappers.TransactionMapper;
import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
import com.mx.cryptomonitor.infrastructure.security.JwtRequestFilter;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;
import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;

@Service
public class TransactionService {
	
    private final Logger logger = LoggerFactory.getLogger(TransactionService.class);

	
    private final TransactionRepository transactionRepository;
    
    @Autowired
    private TransactionMapper transactionMapper;  

    @Autowired
    public TransactionService(TransactionRepository transactionRepository, TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }


    @Transactional
    public TransactionResponse saveTransaction(TransactionRequest transactionRequest) {
    	
    	logger.info("==== Ejecutando método saveTransaction() desde TransactionService === ");

    	try {
			

        Transaction transactionM = transactionMapper.toEntity(transactionRequest);
    	logger.info("...Transacción mapeada antes de guardar: " + transactionM );

        if (transactionM == null) {
            logger.error("❌ Error: transactionMapper.toEntity() devolvió null.");

            throw new IllegalArgumentException("Transaction mapping failed");
        }

        Transaction savedTransaction = transactionRepository.save(transactionM);
    	logger.info("...Transacción después de guardar:" + savedTransaction );

        if (savedTransaction == null) {
            logger.error("❌ Error: transactionRepository.save() devolvió null.");
            throw new IllegalStateException("Transaction could not be saved");
            
        }

        return transactionMapper.toResponse(savedTransaction);
        
		} catch (Exception e) {
			// TODO: handle exception
			logger.error("Exception de saveTransaction ::: "+e);
            throw new RuntimeException("Error al guardar la transacción", e);

		} 
    	
    }

    
    public List<TransactionResponse> getTransactionsByUser(UUID userId) {
        return transactionRepository.findByUserId(userId);
    }

    public List<TransactionResponse> getTransactionsByUserAndSymbol(UUID userId, String assetSymbol) {
        return transactionRepository.findByUserIdAndAssetSymbol(userId, assetSymbol);
    }

}
