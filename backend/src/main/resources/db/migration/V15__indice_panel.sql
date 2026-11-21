-- MA-83 y MA-84: los ingresos y la demanda por ciudad filtran las reservas por estado y fecha de
-- creación. Con este índice no se recorre toda la tabla (100.000 reservas en p95 ≤ 3 s).
CREATE INDEX ix_reserva_estado_creado ON reserva (estado, creado_en);
