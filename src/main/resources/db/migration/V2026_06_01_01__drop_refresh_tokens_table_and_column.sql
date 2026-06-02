-- Redis is the source of truth for refresh tokens since the auth redesign.
-- The refresh_tokens table and the sessions.refresh_token_id FK column are legacy and unused.
ALTER TABLE sessions DROP COLUMN IF EXISTS refresh_token_id;

DROP TABLE IF EXISTS refresh_tokens;
