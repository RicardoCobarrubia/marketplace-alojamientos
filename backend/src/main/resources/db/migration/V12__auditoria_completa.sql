-- MA-87: la auditoría también registra los errores inesperados.
ALTER TABLE auditoria
    DROP CONSTRAINT ck_auditoria_resultado,
    ADD CONSTRAINT ck_auditoria_resultado CHECK (resultado IN ('EXITO', 'RECHAZADO', 'ERROR'));

CREATE INDEX ix_auditoria_accion ON auditoria (accion, ocurrido_en);
