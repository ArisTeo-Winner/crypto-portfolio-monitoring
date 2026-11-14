package com.mx.cryptomonitor.marketdata.application.port.out;

/**
 * Señal de que un proveedor de dividendos NO pudo concluir (ADR-0025 F2): HTTP fallido, sin cuota,
 * timeout o envelope de error/cuota. Distinta de "OK y vacío" (el activo no paga): esto es NO
 * concluyente y NO debe interpretarse como {@code NONE}.
 *
 * <p>La lanzan los adapters en su ruta de fallo (nunca en no-configurado ni en OK-vacío). El
 * orquestador la agrega (si ningún proveedor trajo datos y al menos uno falló, la relanza) y {@code
 * DividendSyncService} la traduce a estado {@code ERROR}.
 *
 * <p>El mensaje NUNCA incluye la URL ni {@code ex.getMessage()} del cliente HTTP (pueden llevar el
 * api-key en el query string): solo nombre de proveedor + símbolo + código/clase.
 */
public class DividendProviderUnavailableException extends RuntimeException {

  public DividendProviderUnavailableException(String message) {
    super(message);
  }
}
