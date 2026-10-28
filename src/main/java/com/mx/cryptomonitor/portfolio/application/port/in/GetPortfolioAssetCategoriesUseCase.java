package com.mx.cryptomonitor.portfolio.application.port.in;

import java.util.List;
import java.util.UUID;

import com.mx.cryptomonitor.portfolio.application.dto.response.AssetCategoryResponse;

/**
 * Devuelve las categorías de activos presentes en el portafolio del usuario autenticado.
 *
 * <p>Solo se incluyen las categorías para las que el usuario tiene al menos una transacción. El
 * orden de la lista es el orden canónico definido por {@code AssetType} (CRYPTO, STOCK, ETF,
 * INDEX), independientemente de cuántos activos tenga el usuario en cada categoría.
 */
public interface GetPortfolioAssetCategoriesUseCase {

  List<AssetCategoryResponse> getCategories(UUID userId);
}
