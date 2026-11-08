-- MA-53 a MA-57: alojamientos y sus catálogos.
-- Sigue las tablas del modelo de datos. Ciudad, tipo, servicio y política de cancelación son
-- tablas (no enums) para poder agregar valores sin cambiar código (MA-53, escenario 3).

-- ─── Catálogos ───────────────────────────────────────────

CREATE TABLE ciudad (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre       VARCHAR(80) NOT NULL,
    departamento VARCHAR(80) NOT NULL,
    CONSTRAINT uk_ciudad_nombre_departamento UNIQUE (nombre, departamento)
);

CREATE TABLE tipo_alojamiento (
    id     SMALLINT    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    nombre VARCHAR(80) NOT NULL
);

CREATE TABLE servicio (
    id     SMALLINT    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    nombre VARCHAR(80) NOT NULL
);

CREATE TABLE politica_cancelacion (
    id         SMALLINT    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo     VARCHAR(40) NOT NULL UNIQUE,
    nombre     VARCHAR(80) NOT NULL,
    parametros JSONB       NOT NULL
);

-- ─── Alojamiento ─────────────────────────────────────────

CREATE TABLE alojamiento (
    id                      BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    anfitrion_id            BIGINT        NOT NULL REFERENCES usuario (id),
    tipo_id                 SMALLINT      NOT NULL REFERENCES tipo_alojamiento (id),
    ciudad_id               BIGINT        NOT NULL REFERENCES ciudad (id),
    politica_cancelacion_id SMALLINT      NOT NULL REFERENCES politica_cancelacion (id),
    titulo                  VARCHAR(120)  NOT NULL,
    descripcion             VARCHAR(2000) NOT NULL,
    ubicacion               VARCHAR(200)  NOT NULL,
    capacidad               INTEGER       NOT NULL,
    precio_noche            NUMERIC(12,2) NOT NULL,
    tarifa_limpieza         NUMERIC(12,2) NOT NULL DEFAULT 0,
    estado                  VARCHAR(20)   NOT NULL DEFAULT 'BORRADOR',
    motivo_rechazo          VARCHAR(500),
    creado_en               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Bloqueo optimista: evita que dos ediciones simultáneas se pisen
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_alojamiento_capacidad CHECK (capacidad > 0),
    CONSTRAINT ck_alojamiento_precio CHECK (precio_noche > 0),
    CONSTRAINT ck_alojamiento_limpieza CHECK (tarifa_limpieza >= 0),
    CONSTRAINT ck_alojamiento_estado CHECK (estado IN
        ('BORRADOR', 'PENDIENTE_REVISION', 'ACTIVA', 'PAUSADA', 'BLOQUEADA'))
);

CREATE INDEX ix_alojamiento_anfitrion ON alojamiento (anfitrion_id);
-- Búsqueda (MA-61): solo ACTIVA, filtrando por ciudad
CREATE INDEX ix_alojamiento_estado_ciudad ON alojamiento (estado, ciudad_id);

CREATE TABLE alojamiento_servicio (
    alojamiento_id BIGINT   NOT NULL REFERENCES alojamiento (id) ON DELETE CASCADE,
    servicio_id    SMALLINT NOT NULL REFERENCES servicio (id),
    PRIMARY KEY (alojamiento_id, servicio_id)
);

CREATE TABLE regla_alojamiento (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alojamiento_id BIGINT       NOT NULL REFERENCES alojamiento (id) ON DELETE CASCADE,
    descripcion    VARCHAR(300) NOT NULL,
    orden          SMALLINT     NOT NULL
);

CREATE TABLE imagen_alojamiento (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alojamiento_id BIGINT       NOT NULL REFERENCES alojamiento (id) ON DELETE CASCADE,
    ruta           VARCHAR(500) NOT NULL,
    es_principal   BOOLEAN      NOT NULL DEFAULT false,
    orden          SMALLINT     NOT NULL
);

-- MA-55. "activo" permite retirar un servicio sin borrar el que ya contrató una reserva.
CREATE TABLE servicio_adicional (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alojamiento_id BIGINT        NOT NULL REFERENCES alojamiento (id) ON DELETE CASCADE,
    nombre         VARCHAR(100)  NOT NULL,
    costo          NUMERIC(12,2) NOT NULL,
    modalidad      VARCHAR(20)   NOT NULL,
    activo         BOOLEAN       NOT NULL DEFAULT true,
    CONSTRAINT ck_servicio_adicional_costo CHECK (costo > 0),
    CONSTRAINT ck_servicio_adicional_modalidad CHECK (modalidad IN ('POR_ESTADIA', 'POR_NOCHE'))
);

CREATE INDEX ix_servicio_adicional_alojamiento ON servicio_adicional (alojamiento_id);

-- ─── Datos iniciales de los catálogos ────────────────────
-- El administrador puede agregar más desde la API sin redesplegar.

INSERT INTO tipo_alojamiento (codigo, nombre) VALUES
    ('APARTAMENTO', 'Apartamento'),
    ('CASA', 'Casa'),
    ('CABANA', 'Cabaña'),
    ('HABITACION', 'Habitación privada'),
    ('FINCA', 'Finca');

INSERT INTO servicio (codigo, nombre) VALUES
    ('WIFI', 'Wifi'),
    ('COCINA', 'Cocina'),
    ('PARQUEADERO', 'Parqueadero'),
    ('PISCINA', 'Piscina'),
    ('AIRE_ACONDICIONADO', 'Aire acondicionado'),
    ('LAVADORA', 'Lavadora'),
    ('TELEVISOR', 'Televisor'),
    ('ADMITE_MASCOTAS', 'Admite mascotas');

INSERT INTO ciudad (nombre, departamento) VALUES
    ('Bogotá', 'Bogotá D.C.'),
    ('Medellín', 'Antioquia'),
    ('Cartagena', 'Bolívar'),
    ('Santa Marta', 'Magdalena'),
    ('Cali', 'Valle del Cauca'),
    ('San Andrés', 'San Andrés y Providencia');

-- Política implementada en el MVP (la regla de devolución se aplica en MA-73)
INSERT INTO politica_cancelacion (codigo, nombre, parametros) VALUES
    ('FLEXIBLE', 'Flexible',
     '{"horasAntesParaReembolsoTotal": 48, "porcentajeReembolsoDespues": 0}');
