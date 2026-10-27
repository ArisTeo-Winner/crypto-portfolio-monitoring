package com.mx.cryptomonitor.asset.application.port.out;

import java.util.Optional;

/**
 * Puerto hacia el proveedor de identidad canonica de instrumentos (OpenFIGI, ADR-0019). Solo
 * identidad: FIGI (composite/share-class) + metadata estatica. No trae precios, dividendos ni
 * corporate actions. El FIGI es inmutable, por eso se resuelve una vez y no se re-consulta.
 */
public interface InstrumentIdentityPort {

  Optional<InstrumentIdentity> resolveByTicker(String ticker, String exchCode);

  record InstrumentIdentity(
      String compositeFigi,
      String shareClassFigi,
      String securityType,
      String marketSector,
      String name) {}
}
