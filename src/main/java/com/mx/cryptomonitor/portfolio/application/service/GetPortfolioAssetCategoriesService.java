package com.mx.cryptomonitor.portfolio.application.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.portfolio.application.dto.response.AssetCategoryResponse;
import com.mx.cryptomonitor.portfolio.application.port.in.GetPortfolioAssetCategoriesUseCase;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;

import lombok.RequiredArgsConstructor;

/**
 * Construye el catálogo de categorías de activos del portafolio del usuario.
 *
 * <p>El catálogo es dinámico: solo incluye los tipos de activos que el usuario tiene registrados en
 * transacciones. Los tipos sin soporte de precios en el motor de portafolio (ej. BONOS) se
 * descartan mediante {@link AssetType#fromSafe}.
 *
 * <p>El orden de las categorías sigue el orden de declaración del enum {@link AssetType}, que
 * representa la relevancia canónica para la plataforma.
 */
@Service
@RequiredArgsConstructor
public class GetPortfolioAssetCategoriesService implements GetPortfolioAssetCategoriesUseCase {

  private final TransactionHistoryPort transactionHistoryPort;

  @Override
  public List<AssetCategoryResponse> getCategories(UUID userId) {
    // Agrupar símbolos por AssetType, descartando tipos sin soporte de precios
    Map<AssetType, Set<String>> symbolsByType =
        transactionHistoryPort.getTransactionsByUser(userId).stream()
            .flatMap(
                snapshot ->
                    AssetType.fromSafe(snapshot.assetType())
                        .map(type -> Map.entry(type, snapshot.assetSymbol().trim().toUpperCase()))
                        .stream())
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toSet())));

    // Respetar el orden canónico del enum; omitir tipos sin transacciones
    return Arrays.stream(AssetType.values())
        .filter(symbolsByType::containsKey)
        .map(
            type -> {
              List<String> symbols = symbolsByType.get(type).stream().sorted().toList();
              return new AssetCategoryResponse(
                  type.name(),
                  type.label(),
                  type.description(),
                  type.icon(),
                  symbols.size(),
                  symbols);
            })
        .toList();
  }
}
