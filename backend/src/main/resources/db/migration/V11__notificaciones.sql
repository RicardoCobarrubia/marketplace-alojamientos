-- MA-75 y MA-76: notificaciones internas generadas por el procesador del outbox (ADR-007).

-- Un evento que falla varias veces se marca FALLIDO para no bloquear a los demás
ALTER TABLE evento_outbox
    DROP CONSTRAINT ck_evento_outbox_estado,
    ADD CONSTRAINT ck_evento_outbox_estado CHECK (estado IN ('PENDIENTE', 'ENTREGADO', 'FALLIDO')),
    ADD COLUMN ultimo_error VARCHAR(500);

CREATE TABLE notificacion (
    id          BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT         NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    -- Sin llave foránea: el outbox se puede depurar sin perder las notificaciones
    evento_id   BIGINT         NOT NULL,
    tipo        VARCHAR(50)    NOT NULL,
    titulo      VARCHAR(120)   NOT NULL,
    mensaje     VARCHAR(1000)  NOT NULL,
    creada_en   TIMESTAMPTZ    NOT NULL,
    leida_en    TIMESTAMPTZ,
    -- Un evento procesado dos veces no notifica dos veces a la misma persona
    CONSTRAINT uq_notificacion_evento_usuario UNIQUE (evento_id, usuario_id)
);
CREATE INDEX ix_notificacion_usuario ON notificacion (usuario_id, creada_en DESC);
