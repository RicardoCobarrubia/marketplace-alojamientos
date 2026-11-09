/** Tipos que reflejan los DTO del backend (UsuarioDTO y AuthController.AuthResponse). */

export type Rol = 'HUESPED' | 'ANFITRION' | 'ADMINISTRADOR';

export type EstadoCuenta = 'ACTIVA' | 'DESACTIVADA' | 'RESTRINGIDA';

export interface Usuario {
  id: number;
  nombre: string;
  email: string;
  telefono: string;
  rol: Rol;
  estadoCuenta: EstadoCuenta;
}

/** Roles que se pueden elegir al registrarse: el administrador no se registra (MA-47). */
export type RolRegistro = Exclude<Rol, 'ADMINISTRADOR'>;

/** Cuerpo de POST /api/auth/registro. */
export interface DatosRegistro {
  nombre: string;
  email: string;
  telefono: string;
  password: string;
  rol: RolRegistro;
}

export interface Credenciales {
  email: string;
  password: string;
}

/** Respuesta de POST /api/auth/login. */
export interface RespuestaLogin {
  token: string;
  tipo: string;
  /** Fecha ISO-8601 en UTC en la que vence el token. */
  expiraEn: string;
  usuario: Usuario;
}

export const NOMBRE_ROL: Record<Rol, string> = {
  HUESPED: 'Huésped',
  ANFITRION: 'Anfitrión',
  ADMINISTRADOR: 'Administrador',
};
