ALTER TABLE transaction
  ADD COLUMN IF NOT EXISTS realized_pnl NUMERIC(18, 2) NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_transaction_user_date_realized
  ON transaction (user_id, transaction_date, transaction_type);
