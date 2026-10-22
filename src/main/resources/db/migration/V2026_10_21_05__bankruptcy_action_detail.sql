-- ADR-0014 F6d: detalle del subtipo BANKRUPTCY a nivel seguridad. 1:1 con corporate_action (CTI,
-- PK compartida). El evento vive sobre la seguridad en quiebra; pivotal_date = fecha efectiva.
--   recovery_per_share = valor recuperado por accion (normalmente 0 para la accion comun; a veces
--                        un residuo en liquidacion). El efecto por usuario (realizar la perdida +
--                        market_status=BANKRUPT) es F6d-2.
CREATE TABLE bankruptcy_action_detail (
    corporate_action_id UUID          NOT NULL,
    recovery_per_share  NUMERIC(18,8) NOT NULL DEFAULT 0,
    currency            VARCHAR(3),
    CONSTRAINT pk_bankruptcy_action_detail PRIMARY KEY (corporate_action_id),
    CONSTRAINT fk_bankruptcy_action_detail_ca FOREIGN KEY (corporate_action_id)
        REFERENCES corporate_action (id) ON DELETE CASCADE
);
