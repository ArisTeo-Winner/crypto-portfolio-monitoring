package com.mx.cryptomonitor.marketdata.application.dto.response;

import java.util.UUID;

/** Respuesta del registro administrado de una fusión (ADR-0015): id del corporate_action creado. */
public record MergerRegisteredResponse(UUID corporateActionId) {}
