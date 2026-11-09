import { createContext } from 'react';
import type { Credenciales, Usuario } from '../types/auth';
import type { Sesion } from './sesion';

export interface ValorAuth {
  sesion: Sesion | null;
  usuario: Usuario | null;
  iniciarSesion: (credenciales: Credenciales) => Promise<Usuario>;
  cerrarSesion: () => void;
}

export const AuthContext = createContext<ValorAuth | null>(null);
