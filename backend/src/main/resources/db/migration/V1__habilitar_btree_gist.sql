-- Habilita la extensión btree_gist, necesaria para la restricción de exclusión
-- que impide guardar dos reservas con fechas superpuestas en el mismo
-- alojamiento (ADR-003). Las tablas se crean en las siguientes migraciones.
CREATE EXTENSION IF NOT EXISTS btree_gist;
