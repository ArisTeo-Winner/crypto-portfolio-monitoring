-- Migración: aumentar capacidad de refresh_token de VARCHAR(255) a TEXT
-- Motivo: los tokens RS256 (JWT firmados con RSA 2048) tienen ~500-600 caracteres,
-- superando el límite anterior de 255. La columna es legado (el token real vive en Redis
-- como hash HMAC-SHA256); no obstante, el esquema debe soportar el wire format completo
-- si algún flujo de auditoría o legacy lo necesita.
ALTER TABLE refresh_tokens
    ALTER COLUMN refresh_token TYPE TEXT;

-- El índice único sigue siendo válido sobre TEXT en PostgreSQL.
