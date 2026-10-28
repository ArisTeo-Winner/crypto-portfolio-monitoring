ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS import_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        CHECK (import_source IN ('MANUAL','DRIVEWEALTH','GBM_STATEMENT','GBM_EQUITY'));

COMMENT ON COLUMN transaction.import_source
    IS 'Procedencia: MANUAL (alta manual) o broker de importacion (DRIVEWEALTH, GBM_STATEMENT, GBM_EQUITY)';
