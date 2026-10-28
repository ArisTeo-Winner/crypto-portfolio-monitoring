-- ADR-0004 (#5): estado de resolucion del logo + timestamp de verificacion.
--   RESOLVED = hay URL de logo.
--   NONE     = confirmado sin logo disponible -> cache de negativos: el pull-once no reintenta.
--   PENDING  = aun no resuelto.
ALTER TABLE asset_catalog ADD COLUMN IF NOT EXISTS logo_status VARCHAR(20);
ALTER TABLE asset_catalog ADD COLUMN IF NOT EXISTS logo_checked_at TIMESTAMPTZ;

-- Backfill de las filas existentes segun tengan o no logo_url.
UPDATE asset_catalog
SET logo_status =
        CASE
            WHEN logo_url IS NOT NULL AND logo_url <> '' THEN 'RESOLVED'
            ELSE 'NONE'
        END
WHERE logo_status IS NULL;
