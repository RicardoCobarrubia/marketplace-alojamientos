-- MA-58, MA-59 (y base de MA-60): calendario de cada alojamiento.
--
-- Los rangos son semiabiertos [inicio, fin): la fecha final es el día de salida y no cuenta
-- como noche. Así una estadía del 10 al 12 y otra del 12 al 14 no se cruzan.
-- El modelo de datos usa una columna daterange; aquí se guardan las dos fechas y el rango se
-- calcula en la restricción con daterange(), que es lo mismo y es más fácil de mapear con JPA.

CREATE TABLE periodo_disponible (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alojamiento_id BIGINT NOT NULL REFERENCES alojamiento (id) ON DELETE CASCADE,
    fecha_inicio   DATE   NOT NULL,
    fecha_fin      DATE   NOT NULL,
    CONSTRAINT ck_periodo_disponible_fechas CHECK (fecha_fin > fecha_inicio),
    -- Los periodos de un mismo alojamiento no se pueden cruzar
    CONSTRAINT ex_periodo_disponible_sin_cruces EXCLUDE USING gist (
        alojamiento_id WITH =,
        daterange(fecha_inicio, fecha_fin, '[)') WITH &&)
);

-- Fechas tomadas por reservas: retenidas mientras se paga (retenido_hasta) o bloqueadas de forma
-- definitiva (retenido_hasta NULL). Al cancelar, fallar el pago o vencer la retención se marca
-- liberado_en y el rango vuelve a quedar libre.
CREATE TABLE periodo_reservado (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alojamiento_id BIGINT      NOT NULL REFERENCES alojamiento (id),
    -- La llave foránea a reserva se agrega en la migración de reservas (MA-66)
    reserva_id     BIGINT      UNIQUE,
    fecha_llegada  DATE        NOT NULL,
    fecha_salida   DATE        NOT NULL,
    retenido_hasta TIMESTAMPTZ,
    liberado_en    TIMESTAMPTZ,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_periodo_reservado_fechas CHECK (fecha_salida > fecha_llegada),
    -- ADR-003: la base de datos garantiza que no haya dos reservas activas superpuestas
    -- en el mismo alojamiento, aunque lleguen solicitudes simultáneas (ASR-01).
    CONSTRAINT ex_periodo_reservado_sin_cruces EXCLUDE USING gist (
        alojamiento_id WITH =,
        daterange(fecha_llegada, fecha_salida, '[)') WITH &&)
        WHERE (liberado_en IS NULL)
);

CREATE INDEX ix_periodo_disponible_alojamiento ON periodo_disponible (alojamiento_id, fecha_inicio);
CREATE INDEX ix_periodo_reservado_alojamiento ON periodo_reservado (alojamiento_id, fecha_llegada)
    WHERE liberado_en IS NULL;
