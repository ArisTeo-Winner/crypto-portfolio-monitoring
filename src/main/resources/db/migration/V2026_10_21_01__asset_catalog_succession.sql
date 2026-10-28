-- ADR-0014 F6a: identidad de sucesion de seguridades — la columna vertebral de merger / de-SPAC /
-- delisting / bankruptcy. El catalogo sigue keyed por 'symbol'; esto solo lo ENRIQUECE.
--   predecessor_symbol = ticker del que esta seguridad es sucesora (p.ej. SECZ.predecessor = CEPT en
--                        un De-SPAC). NULL = no es sucesora de nadie.
--   predecessor_figi   = identidad estable del predecesor (composite FIGI) cuando el ticker cambia.
--   market_status      = estado de la seguridad en su plaza. Default ACTIVE para las filas legadas.
--                        MUTED_BY_MERGER = absorbida por una sucesora (ya no cotiza, pero su historia
--                        de tenencias/eventos sigue viva bajo la sucesora).
ALTER TABLE asset_catalog ADD COLUMN IF NOT EXISTS predecessor_symbol VARCHAR(20);
ALTER TABLE asset_catalog ADD COLUMN IF NOT EXISTS predecessor_figi   VARCHAR(12);
ALTER TABLE asset_catalog
    ADD COLUMN IF NOT EXISTS market_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE asset_catalog DROP CONSTRAINT IF EXISTS chk_asset_catalog_market_status;
ALTER TABLE asset_catalog
    ADD CONSTRAINT chk_asset_catalog_market_status
    CHECK (market_status IN ('ACTIVE', 'MUTED_BY_MERGER', 'DELISTED', 'BANKRUPT', 'SUSPENDED'));

COMMENT ON COLUMN asset_catalog.predecessor_symbol
    IS 'ADR-0014 F6a: ticker predecesor en una sucesion (p.ej. De-SPAC CEPT->SECZ). NULL = ninguno.';
COMMENT ON COLUMN asset_catalog.predecessor_figi
    IS 'ADR-0014 F6a: composite FIGI del predecesor (identidad estable cuando cambia el ticker).';
COMMENT ON COLUMN asset_catalog.market_status
    IS 'ADR-0014 F6a: ACTIVE | MUTED_BY_MERGER | DELISTED | BANKRUPT | SUSPENDED.';

-- Lookup de sucesion: dado un predecesor, encontrar su sucesora. NO unico (defensivo).
CREATE INDEX IF NOT EXISTS idx_asset_catalog_predecessor_symbol
    ON asset_catalog (predecessor_symbol);
