-- Añadir columnas faltantes en la tabla de permisos
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS code VARCHAR(100);
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS description VARCHAR(500);

-- Hacer el código obligatorio y único
ALTER TABLE permissions ALTER COLUMN code SET NOT NULL;
ALTER TABLE permissions ADD CONSTRAINT IF NOT EXISTS permissions_code_unique UNIQUE (code);

-- Crear índices para mejorar el rendimiento
CREATE INDEX IF NOT EXISTS idx_permissions_code ON permissions(code);
CREATE INDEX IF NOT EXISTS idx_roles_name ON roles(name);

-- Crear tabla de relación entre roles y permisos si no existe
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) 
        REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) 
        REFERENCES permissions (id) ON DELETE CASCADE
); 