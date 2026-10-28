package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Representa una categoría de activos disponible en el portafolio del usuario.
 *
 * <p>El frontend usa {@code key} como valor del query-param {@code ?assetType=} para filtrar el
 * historial. El resto de los campos sirven para renderizar el menú de navegación sin lógica
 * adicional en el cliente.
 *
 * <p>Ejemplo de respuesta:
 *
 * <pre>{@code
 * {
 *   "key":         "CRYPTO",
 *   "label":       "Crypto",
 *   "description": "Criptomonedas y tokens digitales",
 *   "icon":        "crypto",
 *   "assetCount":  3,
 *   "symbols":     ["BTC", "ETH", "SOL"]
 * }
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssetCategoryResponse(

    /**
     * Identificador canónico del tipo de activo. Valor válido para {@code ?assetType=} en {@code
     * GET /api/v1/me/portfolio/history}.
     */
    String key,

    /** Nombre para mostrar en el menú de navegación. */
    String label,

    /** Descripción corta para tooltip o submenú. */
    String description,

    /**
     * Nombre del icono sugerido para el frontend. La resolución del icono real (SVG, componente,
     * clase CSS) queda en manos del cliente.
     */
    String icon,

    /** Número de activos distintos que el usuario posee en esta categoría. */
    int assetCount,

    /** Símbolos de los activos del usuario en esta categoría, ordenados alfabéticamente. */
    List<String> symbols) {}
