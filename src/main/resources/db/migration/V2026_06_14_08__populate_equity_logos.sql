-- Reglas de logo_url por tipo de activo:
--   STOCK, ETF, GOVERNMENT_BOND (USD)  -> CompaniesLogo determinista (via symbol)
--   GOVERNMENT_BOND (MXN: CETES/UDIBONO/BONDM) -> null (frontend usa icono generico)
--   CRYPTO                              -> null (CoinGecko en runtime/frontend)
--   INDEX, FOREX                        -> null
UPDATE asset_catalog
SET logo_url = 'https://companieslogo.com/api/starter/stock-symbol/' || symbol
WHERE (asset_type IN ('STOCK', 'ETF')
       OR (asset_type = 'GOVERNMENT_BOND' AND currency = 'USD'))
  AND logo_url IS NULL;

COMMENT ON COLUMN asset_catalog.logo_url IS
    'URL de logo. Equities USD via CompaniesLogo; CRYPTO/INDEX/bonos MX quedan null';
