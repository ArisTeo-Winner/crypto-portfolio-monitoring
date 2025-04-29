package com.mx.cryptomonitor.domain.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(
    name = "portfolio_entry",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "asset_symbol"})
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioEntry {

		@Id
	    @GeneratedValue(strategy = GenerationType.UUID) // Generador automático de UUID
	    @Column(name = "portfolio_entry_id", nullable = false, updatable = false)
	    private UUID portfolioEntryId; // Coincide con "portfolio_entry_id" en la base de datos

	    @ManyToOne
	    @JoinColumn(name = "user_id", nullable = false)
	    private User user; // Relación con el ID del usuario

	    @Column(name = "asset_symbol", nullable = false, length = 10)
	    private String assetSymbol; // Símbolo del activo (BTC, ETH, AAPL, etc.)

	    @Column(name = "asset_type", nullable = false, length = 20)
	    private String assetType; // Tipo de activo (CRYPTO, STOCK, FOREX, etc.)

	    @Column(name = "total_quantity", nullable = false, precision = 18, scale = 8)
	    private BigDecimal totalQuantity = BigDecimal.ZERO; // Cantidad neta actual del activo

	    @Column(name = "total_invested", nullable = false, precision = 18, scale = 2)
	    private BigDecimal totalInvested = BigDecimal.ZERO; // Total invertido

	    @Column(name = "average_price_per_unit", nullable = false, precision = 18, scale = 8)
	    private BigDecimal averagePricePerUnit = BigDecimal.ZERO; // Precio promedio por unidad

	    @Column(name = "last_transaction_price", precision = 18, scale = 8)
	    private BigDecimal lastTransactionPrice; // Precio de la última transacción registrada

	    @Column(name = "current_value", precision = 18, scale = 2)
	    private BigDecimal currentValue; // Valor actual del activo

	    @Column(name = "total_profit_loss", precision = 18, scale = 2)
	    private BigDecimal totalProfitLoss; // Ganancia o pérdida total calculada
	    
	    @UpdateTimestamp
	    @Column(name = "last_updated", nullable = false)
	    private LocalDateTime lastUpdated; // Última actualización

	    @Column(name = "created_at", nullable = false, updatable = false)
	    private final LocalDateTime createdAt = LocalDateTime.now(); // Fecha de creación

	    @Column(name = "updated_at", nullable = false)
	    private LocalDateTime updatedAt = LocalDateTime.now(); // Fecha de última modificación
	    
	    @Version // Bloqueo optimista para concurrencia
	    private Long version;
}
