-- Cada intento de devolución usa su propia clave de idempotencia: si Stripe respondió un error
-- a un intento, el siguiente no recibe esa misma respuesta guardada (MA-68).
ALTER TABLE reserva ADD COLUMN intentos_devolucion INTEGER NOT NULL DEFAULT 0;
