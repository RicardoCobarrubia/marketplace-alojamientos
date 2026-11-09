import { NOMBRE_CAMPO_ALOJAMIENTO } from '../../types/alojamiento';

/** "título, imágenes y reglas" a partir de los campos de INFORMACION_INCOMPLETA. */
export function listarCamposFaltantes(campos: string[]): string {
  const nombres = campos.map((campo) => NOMBRE_CAMPO_ALOJAMIENTO[campo] ?? campo);
  if (nombres.length <= 1) return nombres.join('');
  return `${nombres.slice(0, -1).join(', ')} y ${nombres[nombres.length - 1]}`;
}
