import { ApiError } from './ApiError';

/**
 * Mensaje para mostrar al usuario a partir de cualquier error. Los errores de negocio del backend
 * ya traen un texto pensado para el usuario; en los errores del servidor se agrega el id de
 * correlación para poder rastrearlo en los logs (ADR-012).
 */
export function mensajeParaUsuario(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Ocurrió un error inesperado. Intenta de nuevo.';
  const seguimiento =
    error.status >= 500 && error.correlationId ? ` Código de seguimiento: ${error.correlationId}.` : '';
  return error.message + seguimiento;
}
