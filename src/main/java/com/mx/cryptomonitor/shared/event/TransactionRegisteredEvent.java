package com.mx.cryptomonitor.shared.event;

/**
 * Evento de integración (ADR-0024) que {@code transaction} publica cuando se registra una compra de
 * un activo elegible para dividendos (STOCK/ETF). Lo consume {@code marketdata} para disparar el
 * poblado del historial de dividendos de ese símbolo de forma automática (sin pasar por el endpoint
 * admin).
 *
 * <p>Vive en el módulo {@code shared} (OPEN) a propósito: {@code transaction} ya depende de {@code
 * marketdata} (escucha sus eventos de dividendo/merger), así que si {@code marketdata} dependiera a
 * su vez de un tipo de {@code transaction} se formaría un ciclo de módulos que Spring Modulith
 * prohíbe. Al declararlo en {@code shared}, ambos módulos dependen solo de {@code shared} y no
 * entre sí.
 *
 * <p>{@code assetType} viaja como {@code String} (nombre del enum, p. ej. {@code "STOCK"}) para no
 * acoplar el enum de dominio de {@code transaction}.
 */
public record TransactionRegisteredEvent(String assetSymbol, String assetType) {}
