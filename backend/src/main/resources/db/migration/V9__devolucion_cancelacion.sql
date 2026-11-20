-- MA-73 y MA-74: devolución de la cancelación según la política vigente al reservar.

-- MA-73, escenario 1: con menos de 48 horas de anticipación se devuelve el 50 %
UPDATE politica_cancelacion
SET parametros = '{"horasAntesParaReembolsoTotal": 48, "porcentajeReembolsoDespues": 50}'
WHERE codigo = 'FLEXIBLE';

ALTER TABLE reserva
    -- Código de la política copiada al reservar: decide qué regla de devolución se aplica
    ADD COLUMN politica_cancelacion_codigo VARCHAR(40),
    -- Valor que se devuelve al cancelar (MA-74, escenario 1)
    ADD COLUMN valor_devolucion NUMERIC(12,2),
    -- La devolución ya se calculó pero la pasarela todavía no la aceptó: el conciliador la reintenta
    ADD COLUMN devolucion_pendiente BOOLEAN NOT NULL DEFAULT false,
    -- Una ejecución está pidiendo la devolución a la pasarela hasta este momento: ninguna otra la pide
    ADD COLUMN devolucion_reclamada_hasta TIMESTAMPTZ;

UPDATE reserva r SET politica_cancelacion_codigo = p.codigo
FROM politica_cancelacion p
WHERE p.id = r.politica_cancelacion_id;

-- Las canceladas antes de esta versión no devolvieron nada
UPDATE reserva SET valor_devolucion = 0 WHERE estado = 'CANCELADA';

ALTER TABLE reserva
    ALTER COLUMN politica_cancelacion_codigo SET NOT NULL,
    DROP CONSTRAINT ck_reserva_cancelacion,
    ADD CONSTRAINT ck_reserva_cancelacion CHECK (estado <> 'CANCELADA'
        OR (motivo_cancelacion IS NOT NULL AND cancelada_por IS NOT NULL AND cancelada_en IS NOT NULL
            AND valor_devolucion IS NOT NULL)),
    ADD CONSTRAINT ck_reserva_valor_devolucion CHECK (valor_devolucion IS NULL
        OR (valor_devolucion >= 0 AND valor_devolucion <= total));

CREATE INDEX ix_reserva_devolucion_pendiente ON reserva (cancelada_en) WHERE devolucion_pendiente;
