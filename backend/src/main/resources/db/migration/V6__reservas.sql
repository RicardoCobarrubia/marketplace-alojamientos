-- MA-66 a MA-69 y MA-60: reservas con pago simulado (ADR-002 a ADR-007).

CREATE TABLE reserva (
    id                          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    huesped_id                  BIGINT        NOT NULL REFERENCES usuario (id),
    alojamiento_id              BIGINT        NOT NULL REFERENCES alojamiento (id),
    fecha_llegada               DATE          NOT NULL,
    fecha_salida                DATE          NOT NULL,
    huespedes                   INTEGER       NOT NULL,
    estado                      VARCHAR(20)   NOT NULL,
    -- Copia de la cotización: un cambio de precio o de configuración no afecta la reserva
    noches                      INTEGER       NOT NULL,
    precio_noche                NUMERIC(12,2) NOT NULL,
    subtotal                    NUMERIC(12,2) NOT NULL,
    total_descuentos            NUMERIC(12,2) NOT NULL,
    limpieza                    NUMERIC(12,2) NOT NULL,
    total_servicios             NUMERIC(12,2) NOT NULL,
    porcentaje_tarifa_servicio  NUMERIC(5,2)  NOT NULL,
    tarifa_servicio             NUMERIC(12,2) NOT NULL,
    total                       NUMERIC(12,2) NOT NULL,
    -- Política vigente al reservar: la usa la cancelación (MA-72)
    politica_cancelacion_id     SMALLINT      NOT NULL REFERENCES politica_cancelacion (id),
    politica_cancelacion        JSONB         NOT NULL,
    referencia_pago             VARCHAR(100),
    motivo_fallo                VARCHAR(300),
    -- El pago quedó en duda (la pasarela no respondió ni a la consulta): el conciliador lo revisa
    pago_por_verificar          BOOLEAN       NOT NULL DEFAULT false,
    -- ADR-012: relación entre el id de correlación y el número de reserva
    correlation_id              VARCHAR(100),
    creado_en                   TIMESTAMPTZ   NOT NULL,
    actualizado_en              TIMESTAMPTZ   NOT NULL,
    version                     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_reserva_fechas CHECK (fecha_salida > fecha_llegada),
    CONSTRAINT ck_reserva_huespedes CHECK (huespedes > 0),
    CONSTRAINT ck_reserva_total CHECK (total > 0),
    CONSTRAINT ck_reserva_estado CHECK (estado IN
        ('PENDIENTE_PAGO', 'CONFIRMADA', 'PAGO_FALLIDO', 'EN_REVISION', 'CANCELADA', 'COMPLETADA'))
);
CREATE INDEX ix_reserva_huesped ON reserva (huesped_id, creado_en DESC);
CREATE INDEX ix_reserva_alojamiento ON reserva (alojamiento_id, fecha_llegada);
CREATE INDEX ix_reserva_pendiente ON reserva (creado_en) WHERE estado = 'PENDIENTE_PAGO';
CREATE INDEX ix_reserva_por_verificar ON reserva (actualizado_en) WHERE pago_por_verificar;

CREATE TABLE reserva_servicio (
    id                    BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reserva_id            BIGINT        NOT NULL REFERENCES reserva (id) ON DELETE CASCADE,
    servicio_adicional_id BIGINT        NOT NULL REFERENCES servicio_adicional (id),
    nombre                VARCHAR(100)  NOT NULL,
    modalidad             VARCHAR(20)   NOT NULL,
    costo_unitario        NUMERIC(12,2) NOT NULL,
    cantidad              INTEGER       NOT NULL,
    valor                 NUMERIC(12,2) NOT NULL
);
CREATE INDEX ix_reserva_servicio_reserva ON reserva_servicio (reserva_id);

-- Las fechas retenidas o bloqueadas ya pueden apuntar a su reserva (MA-60)
ALTER TABLE periodo_reservado
    ADD CONSTRAINT fk_periodo_reservado_reserva FOREIGN KEY (reserva_id) REFERENCES reserva (id);

-- ADR-005: Idempotent Receiver. La restricción única decide cuál de los reintentos
-- simultáneos registra la clave; los demás leen lo que quedó guardado.
CREATE TABLE clave_idempotencia (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id     BIGINT       NOT NULL REFERENCES usuario (id),
    clave          VARCHAR(100) NOT NULL,
    huella         VARCHAR(64)  NOT NULL,
    estado         VARCHAR(20)  NOT NULL,
    reserva_id     BIGINT       REFERENCES reserva (id),
    codigo_http    INTEGER,
    respuesta      TEXT,
    creado_en      TIMESTAMPTZ  NOT NULL,
    actualizado_en TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_clave_idempotencia UNIQUE (usuario_id, clave),
    CONSTRAINT ck_clave_idempotencia_estado CHECK (estado IN ('EN_CURSO', 'TERMINADA'))
);
CREATE INDEX ix_clave_idempotencia_creado ON clave_idempotencia (creado_en);

-- ADR-007: Transactional Outbox. El evento se guarda en la misma transacción que el cambio
-- de estado; el procesador que lo entrega se implementa con las notificaciones internas.
CREATE TABLE evento_outbox (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo           VARCHAR(50)  NOT NULL,
    agregado       VARCHAR(30)  NOT NULL,
    agregado_id    BIGINT       NOT NULL,
    payload        JSONB        NOT NULL,
    correlation_id VARCHAR(100),
    estado         VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    intentos       INTEGER      NOT NULL DEFAULT 0,
    creado_en      TIMESTAMPTZ  NOT NULL,
    entregado_en   TIMESTAMPTZ,
    CONSTRAINT ck_evento_outbox_estado CHECK (estado IN ('PENDIENTE', 'ENTREGADO'))
);
CREATE INDEX ix_evento_outbox_pendiente ON evento_outbox (creado_en) WHERE estado = 'PENDIENTE';
