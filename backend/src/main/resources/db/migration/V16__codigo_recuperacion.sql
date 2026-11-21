-- MA-50: códigos de un solo uso para recuperar la contraseña. Se guarda solo su hash: quien lea
-- la tabla no puede usarlos.
CREATE TABLE codigo_recuperacion (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id   BIGINT       NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    codigo_hash  VARCHAR(100) NOT NULL,
    creado_en    TIMESTAMPTZ  NOT NULL,
    vence_en     TIMESTAMPTZ  NOT NULL,
    usado_en     TIMESTAMPTZ,
    intentos     SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT ck_codigo_recuperacion_vence CHECK (vence_en > creado_en),
    CONSTRAINT ck_codigo_recuperacion_intentos CHECK (intentos >= 0)
);
-- El último código de cada usuario es el único que vale
CREATE INDEX ix_codigo_recuperacion_usuario ON codigo_recuperacion (usuario_id, id DESC);
