import type { ReactNode } from 'react';
import { NavLink, Outlet } from 'react-router';
import { LogOut } from 'lucide-react';
import { useAuth } from '../auth/useAuth';
import { Logo } from './Logo';
import './LayoutApp.css';

export interface ItemNavegacion {
  etiqueta: string;
  ruta: string;
  /** true si solo debe marcarse activo en la ruta exacta. */
  exacta?: boolean;
}

interface PropiedadesLayoutApp {
  navegacion?: ItemNavegacion[];
  /** Botón principal a la derecha de la barra, por ejemplo "Nuevo alojamiento". */
  accion?: ReactNode;
}

function iniciales(nombre: string): string {
  return nombre
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((parte) => parte[0]?.toUpperCase())
    .join('');
}

/** Barra superior de las páginas con sesión iniciada, como en el mockup. */
export function LayoutApp({ navegacion = [], accion }: PropiedadesLayoutApp) {
  const { usuario, cerrarSesion } = useAuth();

  return (
    <div className="app">
      <header className="barra-superior">
        <div className="barra-superior__izquierda">
          <Logo />
          {navegacion.length > 0 && (
            <nav className="barra-superior__nav" aria-label="Principal">
              {navegacion.map((item) => (
                <NavLink
                  key={item.ruta}
                  to={item.ruta}
                  end={item.exacta}
                  className={({ isActive }) => (isActive ? 'nav-enlace nav-enlace--activo' : 'nav-enlace')}
                >
                  {item.etiqueta}
                </NavLink>
              ))}
            </nav>
          )}
        </div>

        <div className="barra-superior__derecha">
          {accion}
          {usuario && (
            <>
              <span className="avatar" aria-hidden="true">
                {iniciales(usuario.nombre)}
              </span>
              <span className="barra-superior__nombre">{usuario.nombre}</span>
            </>
          )}
          <button
            type="button"
            className="boton-icono"
            onClick={cerrarSesion}
            aria-label="Cerrar sesión"
            title="Cerrar sesión"
          >
            <LogOut size={17} aria-hidden="true" />
          </button>
        </div>
      </header>

      <main className="app__contenido">
        <Outlet />
      </main>
    </div>
  );
}
