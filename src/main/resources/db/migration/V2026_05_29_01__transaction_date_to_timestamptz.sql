DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name  = 'transaction'
      AND column_name = 'transaction_date'
      AND data_type   = 'timestamp without time zone'
  ) THEN
    ALTER TABLE transaction
      ALTER COLUMN transaction_date TYPE TIMESTAMPTZ
      USING transaction_date AT TIME ZONE 'UTC';

    ALTER TABLE transaction
      ALTER COLUMN created_at TYPE TIMESTAMPTZ
      USING created_at AT TIME ZONE 'UTC';

    ALTER TABLE transaction
      ALTER COLUMN updated_at TYPE TIMESTAMPTZ
      USING updated_at AT TIME ZONE 'UTC';
  END IF;
END $$;