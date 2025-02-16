package com.mx.cryptomonitor.domain.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.application.mappers.TransactionMapper;
import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.PortfolioEntryRepository;
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;
import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;

@Service
public class PortfolioService {
	
    private static final Logger logger = (Logger) LoggerFactory.getLogger(PortfolioService.class);


	private final TransactionRepository transactionRepository;
	private final PortfolioEntryRepository portfolioEntryRepository;
	private final UserRepository userRepository;
	
	@Autowired
	private TransactionMapper transactionMapper; 

	public PortfolioService(
			TransactionRepository transactionRepository,
			PortfolioEntryRepository portfolioEntryRepository,
			TransactionMapper transactionMapper,
			UserRepository userRepository) {
		this.transactionRepository = transactionRepository;
		this.portfolioEntryRepository = portfolioEntryRepository;
		this.transactionMapper = transactionMapper;
		this.userRepository = userRepository;
	}

	public TransactionResponse registerTransaction(TransactionRequest transactionRequest) {
		
		logger.info("=== Ejecutando método registerTransaction() desde PortfolioService ===");
		
		try {
			
            // 1. Mapear el request a la entidad Transaction
            Transaction transactionM = transactionMapper.toEntity(transactionRequest);
            logger.info("... Transacción mapeada antes de asignar campos: " + transactionM);

            // 2. Obtener el usuario desde el repositorio y asignarlo a la transacción
            User user = userRepository.findById(transactionRequest.userId())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + transactionRequest.userId()));
            transactionM.setUser(user);

            // 3. Obtener o crear la entrada en el portafolio
            Optional<PortfolioEntry> portfolioEntryOpt = portfolioEntryRepository
                    .findByUserIdAndAssetSymbol(transactionRequest.userId(), transactionRequest.assetSymbol());

            PortfolioEntry portfolioEntry = portfolioEntryOpt.orElseGet(() ->
                PortfolioEntry.builder()
                    .user(user)
                    .assetSymbol(transactionRequest.assetSymbol())
                    .assetType(transactionRequest.assetType())
                    .totalQuantity(BigDecimal.ZERO)
                    .totalInvested(BigDecimal.ZERO)
                    .averagePricePerUnit(BigDecimal.ZERO)
                    .lastTransactionPrice(null)
                    .currentValue(null)
                    .totalProfitLoss(null)
                    .lastUpdated(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build()
            );

		if ("BUY".equalsIgnoreCase(transactionRequest.transactionType())) {
			BigDecimal newTotalQuantity = portfolioEntry.getTotalQuantity().add(transactionRequest.quantity());
			BigDecimal newTotalInvested = portfolioEntry.getTotalInvested().add(transactionRequest.totalValue());
			portfolioEntry.setTotalQuantity(newTotalQuantity);
			portfolioEntry.setTotalInvested(newTotalInvested);
			portfolioEntry.setAveragePricePerUnit(newTotalInvested.divide(newTotalQuantity, BigDecimal.ROUND_HALF_UP));
			
			logger.info("...Tipo de transacción...: " + transactionM.getTransactionType());

			
		} else if ("SELL".equalsIgnoreCase(transactionRequest.transactionType())) {
			
			portfolioEntry.setTotalQuantity(portfolioEntry.getTotalQuantity().subtract(transactionRequest.quantity()));
			portfolioEntry.setTotalInvested(portfolioEntry.getTotalInvested().subtract(transactionRequest.totalValue()));
			
			logger.info("...Tipo de transacción...: " + transactionM.getTransactionType());
		}

		portfolioEntryRepository.save(portfolioEntry);

		//transactionM.setPortfolioEntryId(portfolioEntry);
		transactionM.setPortfolioEntry(portfolioEntry);
		
		// Guardar transacción
		transactionRepository.save(transactionM);	
				
		return transactionMapper.toResponse(transactionM);	
		
		} catch (Exception e) {
			// TODO: handle exception
			logger.error("❌ Error al guardar la transacción: {}", e.getMessage(), e);
            throw new RuntimeException("Error al guardar la transacción", e);
			
		}
		
	}
	
	private void validateTransaction(TransactionRequest transactionRequest, PortfolioEntry portfolioEntry) {
		if ("SELL".equalsIgnoreCase(transactionRequest.transactionType())) {
		
		}
	}

	public List<TransactionResponse> getTransactionsByUser(UUID userId) {
		return transactionRepository.findByUserId(userId);
	}
}
