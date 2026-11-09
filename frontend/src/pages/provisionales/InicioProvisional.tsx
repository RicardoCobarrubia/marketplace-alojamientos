import { useAuth } from '../../auth/useAuth';
import { NOMBRE_ROL } from '../../types/auth';
import './Provisionales.css';

/**
 * Contenido temporal del inicio de los roles que aún no tienen pantallas (huésped y
 * administrador). Se muestra dentro de LayoutApp.
 */
export default function InicioProvisional() {
  const { usuario } = useAuth();
  if (!usuario) return null;

  return (
    <div className="provisional__contenido">
      <h1>Hola, {usuario.nombre}</h1>
      <p>
        Iniciaste sesión como <strong>{NOMBRE_ROL[usuario.rol]}</strong>. Las pantallas de este rol se construyen en
        las siguientes historias.
      </p>
    </div>
  );
}
