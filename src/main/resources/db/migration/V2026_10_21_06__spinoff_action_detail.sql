-- ADR-0014 F6e: detalle del subtipo SPINOFF a nivel seguridad. 1:1 con corporate_action (CTI, PK
-- compartida). El evento vive sobre la MATRIZ (asset_symbol = ticker que reparte); pivotal_date =
-- fecha efectiva. El tenedor conserva la matriz (base reducida) y recibe acciones de la spinco.
--   spinco_symbol             = ticker de la entidad escindida (lo que recibe el tenedor).
--   shares_per_parent_share   = acciones de spinco por accion de la matriz (ratio de distribucion).
--   cost_basis_allocation_pct = fraccion del costo base de la matriz que migra a la spinco (0..1,
--                               por FMV relativo al spinoff). NULL = aun por determinar (difiere el
--                               efecto, como la fusion hibrida).
-- El efecto por usuario (reparto de base) es F6e-2.
CREATE TABLE spinoff_action_detail (
    corporate_action_id       UUID          NOT NULL,
    spinco_symbol             VARCHAR(20)   NOT NULL,
    shares_per_parent_share   NUMERIC(18,8) NOT NULL,
    cost_basis_allocation_pct NUMERIC(9,8),
    currency                  VARCHAR(3),
    CONSTRAINT pk_spinoff_action_detail PRIMARY KEY (corporate_action_id),
    CONSTRAINT fk_spinoff_action_detail_ca FOREIGN KEY (corporate_action_id)
        REFERENCES corporate_action (id) ON DELETE CASCADE,
    CONSTRAINT ck_spinoff_allocation_pct
        CHECK (cost_basis_allocation_pct IS NULL
               OR (cost_basis_allocation_pct >= 0 AND cost_basis_allocation_pct <= 1))
);
