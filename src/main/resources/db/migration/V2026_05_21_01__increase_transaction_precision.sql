ALTER TABLE public."transaction"
  ALTER COLUMN fee TYPE numeric(18,8);

ALTER TABLE public."transaction"
  ALTER COLUMN total_value TYPE numeric(18,8);

ALTER TABLE public."transaction"
  ALTER COLUMN realized_pnl TYPE numeric(18,8);