-- V2026_06_10_02 ya renombro BONOS->BOND e INDICE->INDEX.
-- Esta migracion finaliza la unificacion: BOND->GOVERNMENT_BOND.
UPDATE transaction SET asset_type = 'GOVERNMENT_BOND' WHERE asset_type = 'BOND';
