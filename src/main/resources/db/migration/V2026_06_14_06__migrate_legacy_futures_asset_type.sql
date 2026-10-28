-- AssetType ya no soporta FUTURES (enum solo tiene CRYPTO, STOCK, ETF,
-- GOVERNMENT_BOND, CORPORATE_BOND, INDEX, FOREX). Filas legacy con
-- asset_type='FUTURES' rompen AssetType.valueOf al leer via JPA.
-- Fallback seguro de lectura: mapear a STOCK.
UPDATE transaction SET asset_type = 'STOCK' WHERE asset_type = 'FUTURES';
