package com.mx.cryptomonitor.domain.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.application.mappers.TransactionMapper;
import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.PortfolioEntryRepository;
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.infrastructure.api.MarketDataService;
import com.mx.cryptomonitor.infrastructure.exceptions.InsufficientFundsException;
import com.mx.cryptomonitor.infrastructure.exceptions.InvalidTransactionException;
import com.mx.cryptomonitor.infrastructure.exceptions.UserNotFoundException;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;

import lombok.extern.slf4j.Slf4j;

import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;

@Slf4j
@Service
public class PortfolioService {
	
    //private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PortfolioEntryRepository portfolioEntryRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private TransactionMapper transactionMapper;
    
    @Autowired
    private MarketDataService marketDataService;
   

    @Transactional
    public TransactionResponse registerTransaction(UUID userdId, TransactionRequest request) {
    	
    	log.info("=== Ejecutando método registerTransaction() desde PortfolioService ===");

        log.info("Registrando transacción para usuario: {}, activo: {}", userdId, request.assetSymbol());
        
        // Validar la entrada
        validateTransactionRequest(request);
        
        // Obtener el usuario
        User user = userRepository.findById(userdId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userdId));
        
        log.info("🔍 Buscando portfolioEntry para userId: {}, assetSymbol: {}", userdId,request.assetSymbol());
        // Obtener o crear la entrada del portafolio
        PortfolioEntry portfolioEntry = getOrCreatePortfolioEntry(user, request);
        log.info("🔎 ¿PortfolioEntry encontrado?: {}", portfolioEntry);

        log.info("🔄 Iniciando updatePortfolioEntry() con portfolioEntry: {}", portfolioEntry);

        // Actualizar la entrada del portafolio según la transacción
         updatePortfolioEntry(portfolioEntry, request);
        
        
        // Crear y guardar la transacción
        Transaction transaction = createTransaction(request, user, portfolioEntry);
        
        transactionRepository.save(transaction);
        
        log.info("Transacción registrada exitosamente: {}", transaction.getTransactionId());
        return transactionMapper.toResponse(transaction);
    }

    private void validateTransactionRequest(TransactionRequest request) {
        if (request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("La cantidad debe ser mayor que cero");
        }
        if (!"BUY".equalsIgnoreCase(request.transactionType()) && !"SELL".equalsIgnoreCase(request.transactionType())) {
            throw new InvalidTransactionException("Tipo de transacción inválido: " + request.transactionType());
        }
    }

    private PortfolioEntry getOrCreatePortfolioEntry(User user, TransactionRequest request) {
    	log.info("=== Ejecutando método getOrCreatePortfolioEntry() desde PortfolioService ===");

        return portfolioEntryRepository.findByUserIdAndAssetSymbol(user.getId(), request.assetSymbol())
                .orElseGet(() -> PortfolioEntry.builder()
                        .user(user)
                        .assetSymbol(request.assetSymbol())
                        .assetType(request.assetType())
                        .totalQuantity(BigDecimal.ZERO)
                        .totalInvested(BigDecimal.ZERO)
                        .averagePricePerUnit(BigDecimal.ZERO)
                        .build());
    }

    @Transactional
    private void updatePortfolioEntry(PortfolioEntry entry, TransactionRequest request) {
    	log.info("=== Ejecutando método updatePortfolioEntry() desde PortfolioService ===");

    	log.info("Contenido de PortfolioEntry: {}",entry);
    	log.info("Contenido de TransactionRequest: {}",request);

        if ("BUY".equalsIgnoreCase(request.transactionType())) {
            BigDecimal newQuantity = entry.getTotalQuantity().add(request.quantity());
            BigDecimal newInvested = entry.getTotalInvested().add(request.totalValue());
            entry.setTotalQuantity(newQuantity);
            entry.setTotalInvested(newInvested);
            entry.setAveragePricePerUnit(newInvested.divide(newQuantity, 8, RoundingMode.HALF_UP));
        } else if ("SELL".equalsIgnoreCase(request.transactionType())) {
            if (entry.getTotalQuantity().compareTo(request.quantity()) < 0) {
                throw new InsufficientFundsException("Cantidad insuficiente para vender");
            }
            BigDecimal newQuantity = entry.getTotalQuantity().subtract(request.quantity());
            BigDecimal newInvested = entry.getTotalInvested().subtract(request.totalValue());
            entry.setTotalQuantity(newQuantity);
            entry.setTotalInvested(newInvested);
            if (newQuantity.compareTo(BigDecimal.ZERO) > 0) {
                entry.setAveragePricePerUnit(newInvested.divide(newQuantity, 8, RoundingMode.HALF_UP));
            } else {
                entry.setAveragePricePerUnit(BigDecimal.ZERO);
            }
        }
        
        // Obtener precio actual desde una API externa        
        Optional<BigDecimal> currentPrice = marketDataService.getCryptoPrice(request.assetSymbol());
         /**/
        BigDecimal price = currentPrice.orElseThrow(() -> 
        new RuntimeException("⚠️No se pudo obtener el precio actual del activo en updatePortfolioEntry()"));
        
        log.info("✅ Último precio de cierre de {}: {}", request.assetSymbol(), price);

        // Calcular current_value
        BigDecimal currentValue = entry.getTotalQuantity().multiply(price)
                .setScale(2, RoundingMode.HALF_UP);
        
        
        // Calcular la ganancia o pérdida total:
        // totalProfitLoss = currentValue - totalInvested
        BigDecimal totalProfitLoss = currentValue.subtract(entry.getTotalInvested() != null ? 
        		entry.getTotalInvested() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        
        entry.setLastTransactionPrice(request.pricePerUnit());   
        entry.setCurrentValue(price);
        entry.setTotalProfitLoss(totalProfitLoss);
        entry.setUpdatedAt(LocalDateTime.now());
        entry.getVersion();
        
 
        log.trace("Valor de version antes de guardar: {}", entry.getVersion());
        
        portfolioEntryRepository.save(entry);
    }

    private Transaction createTransaction(TransactionRequest request, User user, PortfolioEntry portfolioEntry) {
        Transaction transaction = transactionMapper.toEntity(request);
        
        transaction.setUser(user);
        transaction.setPortfolioEntry(portfolioEntry);
        transaction.setTransactionDate(request.transactionDate());
        transaction.setTotalValue(request.pricePerUnit().multiply(request.quantity()).add(request.fee()));
        return transaction;
    }
    
    private void updateComplexPortfolioMetrics() {
    
    }
    
	public List<TransactionResponse> getTransactionsByUser(UUID userId) {
		
		//Optional<User> user = userRepository.findByEmail("");
				
		log.info("User id: {}",userId);
		
		return transactionRepository.findByUserId(userId);
	}
}
