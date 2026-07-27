-- Bootstrap de logo_url determinista para STOCK y CRYPTO (las 15 filas de ETF y
-- GOVERNMENT_BOND en USD ya se poblaron en V2026_06_14_08).
--   STOCK  -> CompaniesLogo (mismo patron que CompaniesLogoAdapter). Valor por defecto:
--             el proximo CatalogSyncService.syncWeekly()/forceFullSync() lo reemplaza con
--             el logo real de Finnhub /stock/profile2.
--   CRYPTO -> spothq/cryptocurrency-icons via jsDelivr, determinista por symbol.
--             CatalogSyncService.STATIC_CRYPTOS fue actualizado en paralelo para no
--             sobreescribir este valor con null en el proximo sync semanal.
-- INDEX y GOVERNMENT_BOND en MXN quedan NULL: no existe proveedor de logo determinista
-- para indices bursatiles ni deuda gubernamental MX.
UPDATE asset_catalog
SET logo_url = 'https://companieslogo.com/api/starter/stock-symbol/' || symbol
WHERE asset_type = 'STOCK'
  AND logo_url IS NULL;

UPDATE asset_catalog
SET logo_url =
    'https://cdn.jsdelivr.net/gh/spothq/cryptocurrency-icons@master/128/color/'
    || lower(symbol) || '.png'
WHERE asset_type = 'CRYPTO'
  AND logo_url IS NULL;
