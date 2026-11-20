-- MA-77 a MA-79: reseñas de una estadía completada, una por reserva, moderadas por el administrador.

CREATE TABLE resena (
    id                   BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Una sola reseña por reserva (MA-78, escenario 2)
    reserva_id           BIGINT        NOT NULL UNIQUE REFERENCES reserva (id),
    alojamiento_id       BIGINT        NOT NULL REFERENCES alojamiento (id),
    huesped_id           BIGINT        NOT NULL REFERENCES usuario (id),
    calificacion         SMALLINT      NOT NULL,
    comentario           VARCHAR(1000) NOT NULL,
    creada_en            TIMESTAMPTZ   NOT NULL,
    oculta               BOOLEAN       NOT NULL DEFAULT false,
    motivo_ocultamiento  VARCHAR(500),
    moderada_en          TIMESTAMPTZ,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_resena_calificacion CHECK (calificacion BETWEEN 1 AND 5),
    CONSTRAINT ck_resena_comentario CHECK (char_length(comentario) BETWEEN 10 AND 1000),
    CONSTRAINT ck_resena_oculta CHECK (NOT oculta OR motivo_ocultamiento IS NOT NULL)
);
-- Las visibles de un alojamiento, las más recientes primero (detalle y promedio)
CREATE INDEX ix_resena_visibles ON resena (alojamiento_id, creada_en DESC) WHERE NOT oculta;
