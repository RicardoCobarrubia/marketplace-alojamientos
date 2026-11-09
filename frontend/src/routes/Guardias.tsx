import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '../auth/useAuth';
import type { Rol } from '../types/auth';
import { RUTAS, rutaInicio } from './rutas';

interface EstadoNavegacion {
  desde?: string;
}

/**
 * Solo deja pasar a usuarios con sesión y con uno de los roles indicados (RF-03). Es solo
 * para la experiencia: el backend vuelve a validar el rol en cada petición (ADR-009).
 */
export function RutaProtegida({ roles }: { roles: readonly Rol[] }) {
  const { usuario } = useAuth();
  const ubicacion = useLocation();

  if (!usuario) {
    const estado: EstadoNavegacion = { desde: ubicacion.pathname + ubicacion.search };
    return <Navigate to={RUTAS.login} replace state={estado} />;
  }
  if (!roles.includes(usuario.rol)) {
    return <Navigate to={rutaInicio(usuario.rol)} replace />;
  }
  return <Outlet />;
}

/**
 * Páginas para quien no ha iniciado sesión (login, registro...). Al iniciar sesión redirige a
 * la página que el usuario intentaba abrir o, si no había ninguna, al inicio de su rol.
 */
export function SoloInvitados() {
  const { usuario } = useAuth();
  const ubicacion = useLocation();

  if (usuario) {
    const desde = (ubicacion.state as EstadoNavegacion | null)?.desde;
    return <Navigate to={desde ?? rutaInicio(usuario.rol)} replace />;
  }
  return <Outlet />;
}

/** Ruta raíz: manda al inicio del rol o al login. */
export function RedireccionInicial() {
  const { usuario } = useAuth();
  return <Navigate to={usuario ? rutaInicio(usuario.rol) : RUTAS.login} replace />;
}
