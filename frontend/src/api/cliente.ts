import { leerSesion, notificarSesionPerdida } from '../auth/sesion';
import { ApiError } from './ApiError';

const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const TIEMPO_MAXIMO_MS = 15_000;

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

interface OpcionesPeticion {
  metodo?: Metodo;
  cuerpo?: unknown;
  cabeceras?: Record<string, string>;
  /** Si es true (por defecto) envía el token en Authorization: Bearer. */
  autenticado?: boolean;
}

/**
 * Hace una petición JSON a la API. Si falla, lanza un {@link ApiError} con el código del backend.
 * Un 401 en una petición autenticada cierra la sesión (token vencido o revocado, ADR-010).
 */
export async function peticion<T>(ruta: string, opciones: OpcionesPeticion = {}): Promise<T> {
  const { metodo = 'GET', cuerpo, cabeceras = {}, autenticado = true } = opciones;

  const headers: Record<string, string> = {
    Accept: 'application/json, application/problem+json',
    ...cabeceras,
  };
  if (cuerpo !== undefined) headers['Content-Type'] = 'application/json';
  if (autenticado) {
    const token = leerSesion()?.token;
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  let respuesta: Response;
  try {
    respuesta = await fetch(`${API_URL}${ruta}`, {
      method: metodo,
      headers,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      signal: AbortSignal.timeout(TIEMPO_MAXIMO_MS),
    });
  } catch (error) {
    const tiempoAgotado = error instanceof DOMException && error.name === 'TimeoutError';
    throw new ApiError({
      status: 0,
      codigo: tiempoAgotado ? 'TIEMPO_AGOTADO' : 'SIN_CONEXION',
      mensaje: tiempoAgotado
        ? 'El servidor tardó demasiado en responder. Intenta de nuevo.'
        : 'No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.',
    });
  }

  if (!respuesta.ok) {
    const error = await ApiError.desdeRespuesta(respuesta);
    if (respuesta.status === 401 && autenticado) notificarSesionPerdida();
    throw error;
  }

  if (respuesta.status === 204) return undefined as T;
  const texto = await respuesta.text();
  return (texto ? JSON.parse(texto) : undefined) as T;
}
