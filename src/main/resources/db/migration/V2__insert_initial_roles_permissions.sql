-- Insertar permisos básicos si no existen
INSERT INTO permissions (id, name, code, description)
SELECT gen_random_uuid(), 'CREATE_PORTFOLIO', 'PORTFOLIO:CREATE', 'Crear nueva cartera'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PORTFOLIO:CREATE');

INSERT INTO permissions (id, name, code, description)
SELECT gen_random_uuid(), 'READ_PORTFOLIO', 'PORTFOLIO:READ', 'Ver carteras'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PORTFOLIO:READ');

INSERT INTO permissions (id, name, code, description)
SELECT gen_random_uuid(), 'UPDATE_PORTFOLIO', 'PORTFOLIO:UPDATE', 'Actualizar carteras'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PORTFOLIO:UPDATE');

INSERT INTO permissions (id, name, code, description)
SELECT gen_random_uuid(), 'DELETE_PORTFOLIO', 'PORTFOLIO:DELETE', 'Eliminar carteras'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PORTFOLIO:DELETE');

INSERT INTO permissions (id, name, code, description)
SELECT gen_random_uuid(), 'CREATE_TRANSACTION', 'TRANSACTION:CREATE', 'Crear nuevas transacciones'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'TRANSACTION:CREATE');

INSERT INTO permissions (id, name, code, description)
SELECT gen_random_uuid(), 'READ_TRANSACTION', 'TRANSACTION:READ', 'Ver transacciones'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'TRANSACTION:READ');

INSERT INTO permissions (id, name, code, description)
SELECT gen_random_uuid(), 'UPDATE_TRANSACTION', 'TRANSACTION:UPDATE', 'Actualizar transacciones'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'TRANSACTION:UPDATE');

INSERT INTO permissions (id, name, code, description)
SELECT gen_random_uuid(), 'DELETE_TRANSACTION', 'TRANSACTION:DELETE', 'Eliminar transacciones'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'TRANSACTION:DELETE');

INSERT INTO permissions (id, name, code, description)
SELECT gen_random_uuid(), 'READ_USER', 'USER:READ', 'Ver usuarios'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'USER:READ');

INSERT INTO permissions (id, name, code, description)
SELECT gen_random_uuid(), 'UPDATE_USER', 'USER:UPDATE', 'Actualizar usuarios'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'USER:UPDATE');

INSERT INTO permissions (id, name, code, description)
SELECT gen_random_uuid(), 'DELETE_USER', 'USER:DELETE', 'Eliminar usuarios'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'USER:DELETE');

INSERT INTO permissions (id, name, code, description)
SELECT gen_random_uuid(), 'CREATE_USER', 'USER:CREATE', 'Crear usuarios'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'USER:CREATE');

-- Insertar roles básicos si no existen
INSERT INTO roles (id, name, description)
SELECT gen_random_uuid(), 'ROLE_ADMIN', 'Administrador del sistema con acceso total'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_ADMIN');

INSERT INTO roles (id, name, description)
SELECT gen_random_uuid(), 'ROLE_USER', 'Usuario estándar con acceso limitado'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_USER');

INSERT INTO roles (id, name, description)
SELECT gen_random_uuid(), 'ROLE_VIEWER', 'Visor con acceso de solo lectura'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_VIEWER');

-- Asignar permisos al rol ADMIN (todos los permisos)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN'
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp 
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

-- Asignar permisos al rol USER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_USER'
AND p.code IN (
    'PORTFOLIO:CREATE', 'PORTFOLIO:READ', 'PORTFOLIO:UPDATE', 'PORTFOLIO:DELETE',
    'TRANSACTION:CREATE', 'TRANSACTION:READ', 'TRANSACTION:UPDATE', 'TRANSACTION:DELETE',
    'USER:READ'
)
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp 
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

-- Asignar permisos al rol VIEWER (solo permisos de lectura)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_VIEWER'
AND p.code IN ('PORTFOLIO:READ', 'TRANSACTION:READ', 'USER:READ')
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp 
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
); 