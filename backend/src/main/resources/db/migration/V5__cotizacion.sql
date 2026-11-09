-- MA-65: reglas de la cotización que configura el administrador sin tocar el código.
-- Una sola fila (id = 1). Las reservas guardan su propia copia de la cotización, así que un
-- cambio aquí no afecta las reservas ya confirmadas.
CREATE TABLE configuracion_cotizacion (
    id                                 SMALLINT     PRIMARY KEY DEFAULT 1,
    porcentaje_tarifa_servicio         NUMERIC(5,2) NOT NULL,
    porcentaje_descuento_estadia_larga NUMERIC(5,2) NOT NULL,
    minimo_noches_descuento            INTEGER      NOT NULL,
    actualizado_en                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Id del administrador que hizo el último cambio (sin llave foránea: es un dato de auditoría)
    actualizado_por                    BIGINT,
    CONSTRAINT ck_configuracion_cotizacion_unica CHECK (id = 1),
    CONSTRAINT ck_configuracion_cotizacion_tarifa
        CHECK (porcentaje_tarifa_servicio BETWEEN 0 AND 100),
    CONSTRAINT ck_configuracion_cotizacion_descuento
        CHECK (porcentaje_descuento_estadia_larga BETWEEN 0 AND 100),
    CONSTRAINT ck_configuracion_cotizacion_minimo CHECK (minimo_noches_descuento >= 1)
);

-- Valores iniciales; el administrador los cambia desde /api/admin/configuracion/cotizacion
INSERT INTO configuracion_cotizacion
    (id, porcentaje_tarifa_servicio, porcentaje_descuento_estadia_larga, minimo_noches_descuento)
VALUES (1, 10.00, 10.00, 7);
