-- MA-47, MA-48, MA-49: usuarios.
-- Igual que en Software 1, el rol es un valor de la cuenta (enum Rol en la clase Usuario)
-- y no una tabla aparte. Agrega lo que pide Software 2: estado de la cuenta, versión de
-- sesión para revocar tokens (ADR-010) y bloqueo temporal por intentos fallidos.

CREATE TABLE usuario (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre            VARCHAR(100) NOT NULL,
    email             VARCHAR(254) NOT NULL UNIQUE,
    telefono          VARCHAR(20)  NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,
    rol               VARCHAR(20)  NOT NULL,
    estado_cuenta     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVA',
    version_sesion    INTEGER      NOT NULL DEFAULT 0,
    intentos_fallidos SMALLINT     NOT NULL DEFAULT 0,
    bloqueado_hasta   TIMESTAMPTZ,
    creado_en         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('HUESPED', 'ANFITRION', 'ADMINISTRADOR')),
    CONSTRAINT ck_usuario_estado_cuenta CHECK (estado_cuenta IN ('ACTIVA', 'DESACTIVADA', 'RESTRINGIDA')),
    -- El correo se guarda siempre en minúsculas para que la unicidad no dependa de mayúsculas
    CONSTRAINT ck_usuario_email_minusculas CHECK (email = lower(email))
);
