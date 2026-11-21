/**
 * Cuerpo de error del backend: Problem Details (RFC 9457) con las propiedades extra que agrega
 * ManejadorGlobalExcepciones y RespuestasSeguridad.
 */
interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  codigo?: string;
  correlationId?: string | null;
  errores?: Record<string, string>;
  campos?: string[];
  /** Número de la reserva en los errores de POST /api/reservas (pago rechazado, sin respuesta...). */
  reservaId?: number | null;
}

interface DatosApiError {
  status: number;
  codigo: string;
  mensaje: string;
  correlationId?: string | null;
  errores?: Record<string, string>;
  campos?: string[];
  reservaId?: number | null;
}

/** Error de una petición a la API. `codigo` es el código estable que envía el backend. */
export class ApiError extends Error {
  readonly status: number;
  readonly codigo: string;
  readonly correlationId: string | null;
  /** Errores por campo cuando el backend responde DATOS_INVALIDOS. */
  readonly errores: Record<string, string>;
  /** Campos que faltan cuando el backend responde INFORMACION_INCOMPLETA. */
  readonly campos: string[];
  /** Reserva que quedó registrada aunque el pago no se completó (POST /api/reservas). */
  readonly reservaId: number | null;

  constructor(datos: DatosApiError) {
    super(datos.mensaje);
    this.name = 'ApiError';
    this.status = datos.status;
    this.codigo = datos.codigo;
    this.correlationId = datos.correlationId ?? null;
    this.errores = datos.errores ?? {};
    this.campos = datos.campos ?? [];
    this.reservaId = datos.reservaId ?? null;
  }

  static async desdeRespuesta(respuesta: Response): Promise<ApiError> {
    let problema: ProblemDetail = {};
    try {
      problema = (await respuesta.json()) as ProblemDetail;
    } catch {
      // Cuerpo vacío o que no es JSON: se usan los valores por defecto.
    }
    return new ApiError({
      status: respuesta.status,
      codigo: problema.codigo ?? `HTTP_${respuesta.status}`,
      mensaje: problema.detail ?? 'Ocurrió un error inesperado. Intenta de nuevo.',
      correlationId: problema.correlationId ?? respuesta.headers.get('X-Correlation-Id'),
      errores: problema.errores,
      campos: problema.campos,
      reservaId: problema.reservaId,
    });
  }
}
