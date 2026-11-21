-- MA-52: al levantar la restricción la cuenta vuelve al estado que tenía. Una cuenta que su dueño
-- desactivó sigue desactivada: solo él la reactiva, confirmando con sus credenciales.

ALTER TABLE usuario ADD COLUMN estado_antes_de_restriccion VARCHAR(20);

ALTER TABLE usuario ADD CONSTRAINT ck_usuario_estado_antes_de_restriccion
    CHECK (estado_antes_de_restriccion IN ('ACTIVA', 'DESACTIVADA'));
