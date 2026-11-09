/** Formatos de moneda y fechas en español de Colombia. */

const PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const FECHA_CORTA = new Intl.DateTimeFormat('es-CO', { day: '2-digit', month: 'short', year: 'numeric' });
const DIA_MES = new Intl.DateTimeFormat('es-CO', { day: '2-digit', month: 'short' });
const MES_ANIO = new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric' });

export function formatearPrecio(valor: number): string {
  return PESOS.format(valor);
}

/** Convierte una fecha local en texto ISO (2026-12-20) sin pasar por UTC. */
export function aIso(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

/** Convierte un texto ISO (2026-12-20) en una fecha local a medianoche. */
export function desdeIso(iso: string): Date {
  const [anio, mes, dia] = iso.split('-').map(Number);
  return new Date(anio, mes - 1, dia);
}

export function hoyIso(): string {
  return aIso(new Date());
}

export function formatearFecha(iso: string): string {
  return FECHA_CORTA.format(desdeIso(iso));
}

/** "01 nov – 13 nov 2026" */
export function formatearRango(inicioIso: string, finIso: string): string {
  return `${DIA_MES.format(desdeIso(inicioIso))} – ${FECHA_CORTA.format(desdeIso(finIso))}`;
}

/** "Noviembre 2026" */
export function formatearMes(fecha: Date): string {
  const texto = MES_ANIO.format(fecha).replace(' de ', ' ');
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/** Lee el valor de un campo numérico; devuelve NaN si está vacío o no es un número. */
export function leerNumero(texto: string): number {
  const limpio = texto.trim();
  return limpio ? Number(limpio) : Number.NaN;
}

export function tieneMaximoDosDecimales(valor: number): boolean {
  return Math.round(valor * 100) === valor * 100;
}
