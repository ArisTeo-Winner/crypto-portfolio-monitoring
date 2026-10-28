-- ADR-0013: separa el EVENTO DE OPERACION bursatil (wall-clock local + nombre IANA de zona) del
-- EVENTO DE REGISTRO de sistema (created_at, UTC). Agrega el binomio inmutable mas una columna
-- derivada e indexada (executed_at_utc) como acelerador de orden cronologico (patron CQRS).
ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS local_execution_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS execution_zone_id    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS executed_at_utc      TIMESTAMPTZ;

COMMENT ON COLUMN transaction.local_execution_time
    IS 'ADR-0013: hora-pared de ejecucion en la plaza (sin zona). Fuente de verdad junto con execution_zone_id.';
COMMENT ON COLUMN transaction.execution_zone_id
    IS 'ADR-0013: nombre IANA de la zona de la plaza (America/New_York, America/Mexico_City, UTC). NO el offset.';
COMMENT ON COLUMN transaction.executed_at_utc
    IS 'ADR-0013: instante UTC derivado del binomio (CQRS) para ordenar/indexar; recalculable, no es fuente de verdad.';

-- Puente de orden: el instante derivado parte del transaction_date actual (que ya es un instante UTC).
-- El binomio local (local_execution_time + execution_zone_id) se poblara en la migracion de datos de
-- F3 (re-import para documentos; centinela 00:00 + zona por geografia para filas manuales legadas).
UPDATE transaction SET executed_at_utc = transaction_date WHERE executed_at_utc IS NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_chronology_utc ON transaction (executed_at_utc DESC);
