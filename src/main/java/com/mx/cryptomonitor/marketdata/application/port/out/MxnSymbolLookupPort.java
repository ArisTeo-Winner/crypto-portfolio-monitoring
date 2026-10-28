package com.mx.cryptomonitor.marketdata.application.port.out;

import java.util.List;

/** Puerto hacia el modulo de transacciones para conocer que emisoras MXN estan en uso. */
public interface MxnSymbolLookupPort {

  List<String> findDistinctMxnSymbols();
}
