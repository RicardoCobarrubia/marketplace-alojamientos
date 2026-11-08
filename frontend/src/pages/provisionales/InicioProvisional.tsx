import { LogOut } from 'lucide-react';
import { useAuth } from '../../auth/useAuth';
import { Logo } from '../../components/Logo';
import { NOMBRE_ROL } from '../../types/auth';
import './Provisionales.css';

function iniciales(nombre: string): string {
  return nombre
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((parte) => parte[0]?.toUpperCase())
    .join('');
}

/**
 * Página de inicio temporal de cada rol. Confirma que el login y la redirección por rol
 * funcionan mientras se construyen las vistas reales del mockup.
 */
export default function InicioProvisional() {
  const { usuario, cerrarSesion } = useAuth();
  if (!usuario) return null;

  return (
    <div className="provisional">
      <header className="barra-superior">
        <Logo />
        <div className="barra-superior__usuario">
          <span className="avatar" aria-hidden="true">
            {iniciales(usuario.nombre)}
          </span>
          <span className="barra-superior__nombre">{usuario.nombre}</span>
          <button type="button" className="boton boton--secundario" onClick={cerrarSesion}>
            <LogOut size={16} aria-hidden="true" />
            Cerrar sesión
          </button>
        </div>
      </header>

      <main className="provisional__contenido">
        <h1>Hola, {usuario.nombre}</h1>
        <p>
          Iniciaste sesión como <strong>{NOMBRE_ROL[usuario.rol]}</strong>. Las pantallas de este rol se construyen
          en las siguientes historias.
        </p>
      </main>
    </div>
  );
}
