-- MA-71 y MA-72: reservas canceladas por el huésped y completadas al pasar la salida.

ALTER TABLE reserva
    ADD COLUMN motivo_cancelacion VARCHAR(500),
    ADD COLUMN cancelada_por      BIGINT REFERENCES usuario (id),
    ADD COLUMN cancelada_en       TIMESTAMPTZ,
    -- Una reserva CANCELADA siempre dice quién, cuándo y por qué (MA-74, escenario 1)
    ADD CONSTRAINT ck_reserva_cancelacion CHECK (estado <> 'CANCELADA'
        OR (motivo_cancelacion IS NOT NULL AND cancelada_por IS NOT NULL AND cancelada_en IS NOT NULL));

-- Las confirmadas que la tarea programada revisa para completarlas (MA-71, escenario 3)
CREATE INDEX ix_reserva_por_completar ON reserva (fecha_salida) WHERE estado = 'CONFIRMADA';
