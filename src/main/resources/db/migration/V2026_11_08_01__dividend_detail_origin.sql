-- SPEC reconciliación de dividendos al borrar una transacción.
-- Marca el origen del dividendo para que la reconciliación (al mutar la tenencia) solo re-derive los
-- AUTO-acreditados y nunca toque los capturados manualmente por el usuario.
-- Default ACCRUED: los dividendos existentes provienen del accrual; el alta manual setea MANUAL en
-- adelante.
ALTER TABLE dividend_detail
    ADD COLUMN origin VARCHAR(10) NOT NULL DEFAULT 'ACCRUED';
