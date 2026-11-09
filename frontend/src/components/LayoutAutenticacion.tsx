import type { ReactNode } from 'react';
import { House } from 'lucide-react';
import { Logo } from './Logo';
import './LayoutAutenticacion.css';

/**
 * Estructura común de las pantallas de acceso (login, registro, recuperar contraseña): panel café
 * a la izquierda y el formulario a la derecha, como en el mockup.
 */
export function LayoutAutenticacion({ children }: { children: ReactNode }) {
  return (
    <div className="autenticacion">
      <aside className="autenticacion__panel">
        <Logo variante="claro" />
        <div className="autenticacion__panel-contenido">
          <h1 className="autenticacion__titulo-panel">Encuentra tu próxima estancia en la región.</h1>
          <p className="autenticacion__subtitulo-panel">
            Alojamientos temporales verificados, con disponibilidad en tiempo real y reservas sin duplicados.
          </p>
        </div>
        <div className="autenticacion__mosaico" aria-hidden="true">
          <span className="autenticacion__tesela autenticacion__tesela--1">
            <House size={22} strokeWidth={1.5} />
          </span>
          <span className="autenticacion__tesela autenticacion__tesela--2">
            <House size={22} strokeWidth={1.5} />
          </span>
          <span className="autenticacion__tesela autenticacion__tesela--3">
            <House size={22} strokeWidth={1.5} />
          </span>
        </div>
      </aside>

      <main className="autenticacion__zona-formulario">
        <div className="autenticacion__tarjeta">{children}</div>
      </main>
    </div>
  );
}
