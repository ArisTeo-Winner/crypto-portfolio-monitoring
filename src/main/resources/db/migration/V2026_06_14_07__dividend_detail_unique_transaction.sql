-- DividendDetail declara @JoinColumn(unique = true) pero el indice creado en
-- V2026_06_10_01/V2026_06_14_01 no es unico. Con ddl-auto=validate la DB permite duplicados.
DROP INDEX IF EXISTS idx_dividend_detail_transaction_id;
ALTER TABLE dividend_detail
    ADD CONSTRAINT uq_dividend_detail_transaction_id UNIQUE (transaction_id);
