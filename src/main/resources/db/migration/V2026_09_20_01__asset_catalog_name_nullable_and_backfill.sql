-- ADR-0008: el catalogo es la unica fuente de verdad de metadatos de activo.
--
-- Causa raiz: el pull-once de iconos sembraba name = simbolo como placeholder y lo marcaba
-- como resuelto; enrichStock traia el logo real de Finnhub pero descartaba el nombre. Resultado:
-- filas STOCK con name = ticker (p.ej. WETO) aunque el proveedor si devuelve la razon social.
--
-- 1) name pasa a ser NULLABLE. Un name NULL significa inequivocamente "aun no resuelto"; el
--    frontend muestra el simbolo en ese caso (decision de render, no dato persistido). Se elimina
--    el placeholder simbolo-como-nombre.
ALTER TABLE asset_catalog ALTER COLUMN name DROP NOT NULL;

-- 2) Limpieza one-shot (no es logica de runtime): anula los placeholders existentes donde el
--    nombre quedo igual al simbolo, para que la reconciliacion diaria (syncDailyRanking) los
--    vuelva a resolver contra el proveedor. Solo STOCK: en CRYPTO el ticker suele ser el nombre
--    real (BNB, XRP) y no debe borrarse.
UPDATE asset_catalog
SET name = NULL
WHERE asset_type = 'STOCK'
  AND name IS NOT NULL
  AND name = symbol;
