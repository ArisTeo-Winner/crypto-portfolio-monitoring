package com.mx.cryptomonitor.domain.services;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.application.mappers.TransactionMapper;
import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
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

    /**/
    public List<TransactionResponse> getTransactionsByUser(UUID userId) {
        return transactionRepository.findByUserId(userId);
    }

    public List<TransactionResponse> getTransactionsByUserAndSymbol(UUID userId, String assetSymbol) {
        return transactionRepository.findByUserIdAndAssetSymbol(userId, assetSymbol);
    }
    public List<TransactionResponse> getTransactionsByUserId(UUID userId, String assetSymbol) {
        if (assetSymbol == null || assetSymbol.isEmpty()) {
            return transactionRepository.findByUserId(userId);
        }

        try {
            //AssetType type = AssetType.valueOf(assetType.toUpperCase());
        	return transactionRepository.findByUserIdAndAssetSymbol(userId, assetSymbol);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Activo inválido: " + assetSymbol);
        }
    }
    
    public List<TransactionResponse> getTransactionsUser(UUID userId, String assetSymbol, String assetType, String transactionType){
    	
    	if (assetSymbol != null && assetSymbol.isEmpty()) assetSymbol = null;
    	if (assetType != null && assetType.isEmpty()) assetType = null;
    	if (transactionType != null && transactionType.isEmpty()) transactionType = null;
    	
    	if (assetSymbol != null && assetType != null && transactionType !=null) {    		
			List<Transaction> transaction = transactionRepository.findByUserIdAndAssetSymbolAndAssetTypeAndTransactionType(userId, assetSymbol, assetType, transactionType);		
			return transaction.stream()
					.map(transactionMapper::toResponse)
					.collect(Collectors.toList());
		}
    	if (assetSymbol != null) {
			return transactionRepository.findByUserIdAndAssetSymbol(userId, assetSymbol);
		}
    	if (assetType != null) {
    		return transactionRepository.findByUserIdAndAssetType(userId, assetType);
    	}
    	
    	if(transactionType != null) {
    		return transactionRepository.findByUserIdAndTransactionType(userId, transactionType);
    	}    	
    	return transactionRepository.findByUserId(userId);    	
    }
    
    public void deleteTransactionById(UUID idTransaction) {
    	
    	Transaction transaction=  transactionRepository.findById(idTransaction)
    			.orElseThrow(() -> new RuntimeException("Transaction no encontrado"));
    	
    	transactionRepository.deleteById(transaction.getTransactionId());
    	
    }

}
