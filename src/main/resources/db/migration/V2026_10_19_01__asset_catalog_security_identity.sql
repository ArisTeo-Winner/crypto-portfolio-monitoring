-- ADR-0014 F1a: security master — identificadores universales GRATIS para resolver corporate
-- actions (dividendos, splits, fusiones) de forma estable, independiente del ticker.
--   composite_figi   = FIGI composite (identidad de seguridad; gratis via OpenFIGI). Futura llave
--                      canonica. El catalogo sigue keyed por 'symbol'; esto solo lo ENRIQUECE.
--   share_class_figi = FIGI share-class global (une cross-border, p.ej. una accion US y su
--                      cross-listing en SIC comparten share-class).
--   cik              = Central Index Key (emisor SEC; gratis via EDGAR) -> filings/fundamentals.
--   isin             = opcional (free-only; util para foco europeo). NO se guarda CUSIP (licenciado;
--                      FIGI lo sustituye sin bloqueos comerciales).
ALTER TABLE asset_catalog ADD COLUMN IF NOT EXISTS composite_figi   VARCHAR(12);
ALTER TABLE asset_catalog ADD COLUMN IF NOT EXISTS share_class_figi VARCHAR(12);
ALTER TABLE asset_catalog ADD COLUMN IF NOT EXISTS cik              VARCHAR(10);
ALTER TABLE asset_catalog ADD COLUMN IF NOT EXISTS isin             VARCHAR(12);

COMMENT ON COLUMN asset_catalog.composite_figi
    IS 'ADR-0014: FIGI composite (identidad de seguridad, gratis OpenFIGI). Futura llave canonica.';
COMMENT ON COLUMN asset_catalog.share_class_figi
    IS 'ADR-0014: FIGI share-class global; une la misma accion cross-border (p.ej. US <-> SIC).';
COMMENT ON COLUMN asset_catalog.cik
    IS 'ADR-0014: Central Index Key (emisor SEC, gratis EDGAR); link a filings/fundamentals.';
COMMENT ON COLUMN asset_catalog.isin
    IS 'ADR-0014: ISIN opcional (free-only; util EU). NO se guarda CUSIP (licenciado).';

-- Indice NO unico a proposito: una seguridad cross-listed puede tener varias filas (simbolos) con el
-- mismo composite_figi; el lookup por FIGI hace el fan-out a esos simbolos/tenencias.
CREATE INDEX IF NOT EXISTS idx_asset_catalog_composite_figi ON asset_catalog (composite_figi);
