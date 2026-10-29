package com.mx.cryptomonitor.portfolio.domain.exception;

/**
 * No existe ningun proveedor de historial de precios que soporte el {@code AssetType} solicitado
 * (p.ej. {@code GOVERNMENT_BOND}, que se valua por devengo y no por precio de mercado — ver
 * ADR-0018).
 *
 * <p>Extiende {@link MarketDataServerException} a proposito: es un error recuperable por activo, no
 * global. Asi, el manejo de fallback existente (p.ej. {@code
 * GetPortfolioTotalHistoryService.toAssetInput}) lo trata como "activo no disponible" y responde de
 * forma parcial, en vez de propagar un 500 que tumbe toda la grafica por un unico activo.
 */
public class NoMarketDataProviderException extends MarketDataServerException {
  public NoMarketDataProviderException(String message) {
    super(message);
  }
}
