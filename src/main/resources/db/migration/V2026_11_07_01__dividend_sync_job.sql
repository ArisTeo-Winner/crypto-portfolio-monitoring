-- ADR-0028 F2: cola durable de procesamiento de dividendos (Postgres-as-queue, como
-- statement_import_job). Al registrar una compra se encola el simbolo; un worker lo drena
-- (sync + replay de accrual) con retry/backoff y dead-letter, sobreviviendo reinicios.
CREATE TABLE dividend_sync_job (
    id              UUID         NOT NULL,
    asset_symbol    VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    CONSTRAINT pk_dividend_sync_job PRIMARY KEY (id),
    CONSTRAINT ck_dividend_sync_job_status CHECK (status IN
        ('QUEUED','PROCESSING','COMPLETED','DEAD_LETTER'))
);

-- Reclamo de la cola (FOR UPDATE SKIP LOCKED sobre QUEUED vencidos / PROCESSING abandonados).
CREATE INDEX idx_dividend_sync_job_claim ON dividend_sync_job (status, next_attempt_at);
-- Dedup ATOMICO (ADR-0028 F4): a lo sumo UN job activo por simbolo. Cierra la carrera del
-- exists-check (dos enqueue concurrentes no crean jobs duplicados); el enqueue atrapa la violacion
-- y la trata como "ya encolado". Tambien sirve de lookup por simbolo activo.
CREATE UNIQUE INDEX uq_dividend_sync_job_active_symbol
    ON dividend_sync_job (asset_symbol) WHERE status IN ('QUEUED', 'PROCESSING');
