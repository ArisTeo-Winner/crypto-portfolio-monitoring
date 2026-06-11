package com.mx.cryptomonitor.asset.infrastructure.outbound.companieslogo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CompaniesLogoAdapter {

  private static final Logger log = LoggerFactory.getLogger(CompaniesLogoAdapter.class);
  private static final String BASE = "https://companieslogo.com";

  /**
   * Construye la URL del logo sin llamada HTTP previa. CompaniesLogo usa URLs predecibles; el
   * frontend maneja 404 con placeholder.
   */
  public String buildLogoUrl(String ticker) {
    if (ticker == null || ticker.isBlank()) return null;
    return BASE + "/api/starter/stock-symbol/" + ticker.toUpperCase();
  }
}
