-- MA-80 a MA-82: moderación de alojamientos, usuarios y reservas, con registro de auditoría.

ALTER TABLE alojamiento ADD COLUMN motivo_bloqueo VARCHAR(500);
ALTER TABLE usuario ADD COLUMN motivo_restriccion VARCHAR(500);

-- Registro de auditoría (MA-87): solo se insertan filas, nunca se cambian ni se borran
CREATE TABLE auditoria (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ocurrido_en      TIMESTAMPTZ   NOT NULL,
    -- Sin llave foránea: el registro se conserva aunque el usuario desaparezca
    actor_id         BIGINT,
    actor_rol        VARCHAR(20),
    accion           VARCHAR(60)   NOT NULL,
    entidad          VARCHAR(40),
    entidad_id       BIGINT,
    estado_anterior  VARCHAR(30),
    estado_nuevo     VARCHAR(30),
    resultado        VARCHAR(20)   NOT NULL,
    detalle          VARCHAR(500),
    correlation_id   VARCHAR(100),
    CONSTRAINT ck_auditoria_resultado CHECK (resultado IN ('EXITO', 'RECHAZADO'))
);
CREATE INDEX ix_auditoria_entidad ON auditoria (entidad, entidad_id, ocurrido_en);
CREATE INDEX ix_auditoria_actor ON auditoria (actor_id, ocurrido_en);

CREATE FUNCTION auditoria_inmutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'El registro de auditoría no se puede modificar ni borrar';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_auditoria_inmutable
    BEFORE UPDATE OR DELETE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria_inmutable();
