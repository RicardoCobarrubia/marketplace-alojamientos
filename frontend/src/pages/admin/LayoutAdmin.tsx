import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';
import {
  Bell,
  ChartColumn,
  ClipboardList,
  CreditCard,
  Download,
  FileText,
  House,
  ListChecks,
  LogOut,
  Tags,
  TriangleAlert,
  UserRound,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { listarAlojamientosAdmin } from '../../api/admin';
import { useAuth } from '../../auth/useAuth';
import { Logo } from '../../components/Logo';
import { RUTAS } from '../../routes/rutas';
import './LayoutAdmin.css';

interface ItemMenu {
  etiqueta: string;
  icono: LucideIcon;
  /** Sin ruta: el módulo aún no existe en el backend; se muestra pero no navega. */
  ruta?: string;
  exacta?: boolean;
  contador?: number;
}

function iniciales(nombre: string): string {
  return nombre
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((parte) => parte[0]?.toUpperCase())
    .join('');
}

/** Estructura del panel de administración del mockup: barra lateral café y contenido a la derecha. */
export default function LayoutAdmin() {
  const { usuario, cerrarSesion } = useAuth();
  const { pathname } = useLocation();
  const [pendientes, setPendientes] = useState<number | undefined>();

  // Se recalcula al navegar para que el contador baje después de aprobar o rechazar.
  useEffect(() => {
    let activo = true;
    listarAlojamientosAdmin('PENDIENTE_REVISION')
      .then((lista) => activo && setPendientes(lista.length))
      .catch(() => activo && setPendientes(undefined));
    return () => {
      activo = false;
    };
  }, [pathname]);

  const menu: ItemMenu[] = [
    { etiqueta: 'Panel', icono: ChartColumn, ruta: RUTAS.admin, exacta: true },
    { etiqueta: 'Revisión de publicaciones', icono: ListChecks, ruta: RUTAS.adminPublicaciones, contador: pendientes },
    { etiqueta: 'Alojamientos', icono: House, ruta: RUTAS.adminAlojamientos },
    { etiqueta: 'Catálogos', icono: Tags, ruta: RUTAS.adminCatalogos },
    { etiqueta: 'Usuarios', icono: Users },
    { etiqueta: 'Reservas en revisión', icono: TriangleAlert },
    { etiqueta: 'Reseñas', icono: ClipboardList },
    { etiqueta: 'Tarifas', icono: CreditCard },
    { etiqueta: 'Reportes', icono: Download },
    { etiqueta: 'Auditoría', icono: FileText },
  ];

  return (
    <div className="admin">
      <aside className="admin__lateral">
        <Logo variante="claro" />
        <nav className="admin__menu" aria-label="Administración">
          <span className="admin__seccion">Administración</span>
          {menu.map((item) => (
            <ElementoMenu key={item.etiqueta} item={item} />
          ))}
        </nav>
        <div className="admin__pie">
          <ElementoMenu item={{ etiqueta: 'Notificaciones', icono: Bell }} />
          <ElementoMenu item={{ etiqueta: 'Mi perfil', icono: UserRound }} />
          <button type="button" className="admin__item" onClick={cerrarSesion}>
            <LogOut size={16} aria-hidden="true" />
            <span>Cerrar sesión</span>
          </button>
        </div>
      </aside>

      <div className="admin__contenedor">
        {usuario && (
          <div className="admin__usuario">
            <span className="avatar" aria-hidden="true">
              {iniciales(usuario.nombre)}
            </span>
            <span>{usuario.nombre}</span>
          </div>
        )}
        <main className="admin__contenido">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function ElementoMenu({ item }: { item: ItemMenu }) {
  const Icono = item.icono;
  const contenido = (
    <>
      <Icono size={16} aria-hidden="true" />
      <span>{item.etiqueta}</span>
      {item.contador !== undefined && item.contador > 0 && (
        <span className="admin__contador" aria-label={`${item.contador} pendientes`}>
          {item.contador}
        </span>
      )}
    </>
  );

  // Sin ruta: el módulo aún no existe en el backend. Se ve igual que los demás, pero no hace nada.
  if (!item.ruta) {
    return (
      <button type="button" className="admin__item">
        {contenido}
      </button>
    );
  }
  return (
    <NavLink
      to={item.ruta}
      end={item.exacta}
      className={({ isActive }) => (isActive ? 'admin__item admin__item--activo' : 'admin__item')}
    >
      {contenido}
    </NavLink>
  );
}
