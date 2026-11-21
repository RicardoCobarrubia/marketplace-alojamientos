import { createContext } from 'react';
import type { Credenciales, Usuario } from '../types/auth';
import type { Sesion } from './sesion';

export interface ValorAuth {
  sesion: Sesion | null;
  usuario: Usuario | null;
  iniciarSesion: (credenciales: Credenciales) => Promise<Usuario>;
  /** Reactiva una cuenta desactivada con sus credenciales e inicia sesión (MA-52). */
  reactivarCuenta: (credenciales: Credenciales) => Promise<Usuario>;
  /** Reemplaza los datos del usuario en la sesión, por ejemplo después de editar el perfil. */
  actualizarUsuario: (usuario: Usuario) => void;
  cerrarSesion: () => void;
}

export const AuthContext = createContext<ValorAuth | null>(null);
