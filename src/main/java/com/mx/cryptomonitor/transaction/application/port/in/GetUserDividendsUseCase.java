package com.mx.cryptomonitor.transaction.application.port.in;

import java.util.List;
import java.util.UUID;

import com.mx.cryptomonitor.transaction.application.dto.response.UserDividendResponse;

/**
 * Read-model de ingresos por dividendo del usuario (ADR-0029): lista los dividendos YA recibidos
 * (fecha de pago &lt;= hoy) con los montos del usuario, para la vista "Mis Dividendos". Solo los
 * dividendos del usuario; nunca de simbolos ajenos.
 */
public interface GetUserDividendsUseCase {

  List<UserDividendResponse> getUserDividends(UUID userId);
}
