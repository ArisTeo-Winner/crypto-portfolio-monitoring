-- Soporte de reintentos con backoff y dead-letter para statement_import_job.
-- attempt_count: numero de intentos fallidos con error transitorio (no cuenta errores de datos,
-- esos van directo a DEAD_LETTER). next_attempt_at: cuando un job vuelve a QUEUED tras un fallo
-- transitorio, el worker no debe reclamarlo antes de esta marca (backoff exponencial, ver
-- StatementImportJobLifecycleService.markFailed).
ALTER TABLE statement_import_job
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ;
