-- Cola de procesamiento asincrono para la ingesta de estados de cuenta/confirmaciones
-- de broker (modulo statementimport). Cada archivo subido crea una fila QUEUED; un
-- worker @Scheduled la reclama con SELECT ... FOR UPDATE SKIP LOCKED (ver
-- StatementImportJobRepository.claimBatch) para poder escalar a varias instancias sin
-- coordinacion adicional.
CREATE TABLE IF NOT EXISTS statement_import_job (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL,
    job_type      VARCHAR(30)   NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    file_name     VARCHAR(255),
    file_content  BYTEA         NOT NULL,
    result_json   TEXT,
    error_message TEXT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ
);

-- Indice parcial: solo cubre las filas que el worker realmente escanea al reclamar.
CREATE INDEX IF NOT EXISTS idx_statement_import_job_queue
    ON statement_import_job (created_at)
    WHERE status IN ('QUEUED', 'PROCESSING');

CREATE INDEX IF NOT EXISTS idx_statement_import_job_user
    ON statement_import_job (user_id, created_at DESC);
