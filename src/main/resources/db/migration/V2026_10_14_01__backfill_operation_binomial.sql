-- ADR-0013 F3: backfill del binomio de operacion para filas LEGADAS (creadas antes de F2, con
-- local_execution_time NULL). Hora desconocida -> centinela 00:00 (valor estandar en finanzas, no
-- basura); zona derivada por geografia del activo (crypto -> UTC; MXN/BMV -> Mexico_City; USD -> NY).
-- local_execution_time = el dia calendario del transaction_date (en UTC) a las 00:00 locales.
-- executed_at_utc se resuelve del binomio (00:00 en la plaza -> instante UTC).
--
-- Las confirmaciones DriveWealth/GBM se corrigen aparte RE-IMPORTANDO (sobreescriben el centinela
-- con la Execution Time real); esta migracion solo da un binomio razonable y reversible mientras.
UPDATE transaction
SET execution_zone_id = derived.zone,
    local_execution_time = derived.local_ts,
    executed_at_utc = derived.local_ts AT TIME ZONE derived.zone
FROM (
    SELECT
        transaction_id,
        CASE
            WHEN asset_type = 'CRYPTO' THEN 'UTC'
            WHEN currency = 'MXN' OR exchange = 'BMV' THEN 'America/Mexico_City'
            WHEN currency = 'USD' THEN 'America/New_York'
            ELSE 'UTC'
        END AS zone,
        (transaction_date AT TIME ZONE 'UTC')::date::timestamp AS local_ts
    FROM transaction
    WHERE local_execution_time IS NULL
) AS derived
WHERE transaction.transaction_id = derived.transaction_id;
