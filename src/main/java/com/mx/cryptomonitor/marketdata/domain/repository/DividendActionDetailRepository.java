package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mx.cryptomonitor.marketdata.domain.model.DividendActionDetailEntity;

/** Acceso al detalle del subtipo DIVIDEND (ADR-0014 F3a), llaveado por corporate_action_id. */
public interface DividendActionDetailRepository
    extends JpaRepository<DividendActionDetailEntity, UUID> {

  /**
   * DPS recurring de una seguridad para el crecimiento de dividendo (ADR-0014 F5): devuelve {@code
   * [exDate (pivotalDate), dps, currency]} usando split_adjusted_dps cuando existe (comparable
   * entre splits) y excluyendo SPECIAL. {@code currency} es la moneda nativa declarada
   * (multi-mercado MXN/USD). El agregado anual y la CAGR los calcula el dominio; la moneda la
   * propaga el servicio.
   */
  @Query(
      "SELECT ca.pivotalDate, COALESCE(d.splitAdjustedDps, d.amountPerShare), d.currency"
          + " FROM DividendActionDetailEntity d JOIN d.corporateAction ca"
          + " WHERE ca.assetSymbol = :symbol"
          + " AND ca.actionType = com.mx.cryptomonitor.marketdata.domain.model"
          + ".CorporateActionType.DIVIDEND"
          + " AND d.distributionType = com.mx.cryptomonitor.marketdata.domain.model"
          + ".DividendDistributionType.RECURRING")
  List<Object[]> findRecurringDpsBySymbol(@Param("symbol") String symbol);
}
