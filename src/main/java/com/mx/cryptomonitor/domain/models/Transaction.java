package com.mx.cryptomonitor.domain.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "transaction")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
	

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Generador automático de UUID
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId; // Coincide con "transaction_id" en la base de datos

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Relación con el ID del usuario
    
    @ManyToOne
    @JoinColumn(name = "portfolio_entry_id", nullable = false)
    private PortfolioEntry portfolioEntry; // Coincide con "portfolio_entry_id" en la base de datos
    
    @Column(name = "asset_symbol", nullable = false, length = 10)
    private String assetSymbol; // Símbolo del activo (BTC, ETH, AAPL, etc.)

    @Column(name = "asset_type", nullable = false, length = 20)
    private String assetType; // Tipo de activo (CRYPTO, STOCK, FOREX, etc.)

    @Column(name = "transaction_type", nullable = false, length = 10)
    private String transactionType; // BUY o SELL

    @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity; // Cantidad comprada o vendida

    @Column(name = "price_per_unit", nullable = false, precision = 18, scale = 8)
    private BigDecimal pricePerUnit; // Precio por unidad al momento de la transacción

    @Column(name = "total_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalValue; // Valor total (quantity * pricePerUnit)

    
    @Column(name = "transaction_date")
    private LocalDateTime transactionDate = LocalDateTime.now(); // Fecha y hora de la transacción

    @Column(name = "fee", precision = 18, scale = 2)
    private BigDecimal fee = BigDecimal.ZERO; // Comisión de la transacción (opcional)

    @Column(name = "price_at_transaction", precision = 18, scale = 8)
    private BigDecimal priceAtTransaction; // Precio del activo al momento de la transacción (opcional)

    @Column(name = "notes")
    private String notes; // Notas adicionales sobre la transacción

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(); // Fecha de creación de la transacción

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now(); // Fecha de última modificación
    
    @JsonCreator
    public Transaction(@JsonProperty("transactionDate") LocalDateTime transactionDate) {
        this.transactionDate = (transactionDate != null) ? transactionDate : LocalDateTime.now();
        
    }

}
