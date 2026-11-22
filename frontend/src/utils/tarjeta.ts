/**
 * Validaciones de la tarjeta del pago simulado (RF-24). Los datos solo se revisan en el navegador:
 * no se guardan ni se envían al backend, que cobra con su pasarela simulada.
 */

export type MarcaTarjeta = 'VISA' | 'MASTERCARD' | 'AMEX' | 'OTRA';

export const NOMBRE_MARCA: Record<MarcaTarjeta, string> = {
  VISA: 'Visa',
  MASTERCARD: 'Mastercard',
  AMEX: 'American Express',
  OTRA: 'Tarjeta',
};

export function soloDigitos(texto: string): string {
  return texto.replace(/\D/g, '');
}

export function marcaDe(numero: string): MarcaTarjeta {
  const n = soloDigitos(numero);
  if (/^4/.test(n)) return 'VISA';
  if (/^3[47]/.test(n)) return 'AMEX';
  const prefijo = Number(n.slice(0, 4));
  if (/^5[1-5]/.test(n) || (prefijo >= 2221 && prefijo <= 2720)) return 'MASTERCARD';
  return 'OTRA';
}

/** Largo del número según la marca: American Express usa 15 dígitos, las demás 16. */
export function largoNumero(marca: MarcaTarjeta): number {
  return marca === 'AMEX' ? 15 : 16;
}

export function largoCvv(marca: MarcaTarjeta): number {
  return marca === 'AMEX' ? 4 : 3;
}

/** "4242424242424242" → "4242 4242 4242 4242"; American Express va en grupos de 4-6-5. */
export function formatearNumero(numero: string): string {
  const n = soloDigitos(numero);
  if (marcaDe(n) === 'AMEX') {
    return [n.slice(0, 4), n.slice(4, 10), n.slice(10, 15)].filter(Boolean).join(' ');
  }
  return n.slice(0, 16).replace(/(\d{4})(?=\d)/g, '$1 ');
}

/** Algoritmo de Luhn: detecta números mal escritos, como hacen las pasarelas reales. */
export function pasaLuhn(numero: string): boolean {
  const n = soloDigitos(numero);
  let suma = 0;
  for (let i = 0; i < n.length; i++) {
    let digito = Number(n[n.length - 1 - i]);
    if (i % 2 === 1) {
      digito *= 2;
      if (digito > 9) digito -= 9;
    }
    suma += digito;
  }
  return n.length > 0 && suma % 10 === 0;
}

/** "1228" → "12/28" mientras se escribe. */
export function formatearVencimiento(texto: string): string {
  const n = soloDigitos(texto).slice(0, 4);
  return n.length > 2 ? `${n.slice(0, 2)}/${n.slice(2)}` : n;
}

/** La tarjeta sirve hasta el último día del mes de vencimiento. */
export function vencimientoValido(texto: string, hoy = new Date()): boolean {
  const coincide = /^(\d{2})\/(\d{2})$/.exec(texto);
  if (!coincide) return false;
  const mes = Number(coincide[1]);
  const anio = 2000 + Number(coincide[2]);
  if (mes < 1 || mes > 12) return false;
  return anio > hoy.getFullYear() || (anio === hoy.getFullYear() && mes >= hoy.getMonth() + 1);
}

export interface DatosTarjeta {
  titular: string;
  numero: string;
  vencimiento: string;
  cvv: string;
}

export type ErroresTarjeta = Partial<Record<keyof DatosTarjeta, string>>;

export function validarTarjeta(t: DatosTarjeta): ErroresTarjeta {
  const errores: ErroresTarjeta = {};
  const marca = marcaDe(t.numero);
  const numero = soloDigitos(t.numero);
  if (t.titular.trim().length < 3) errores.titular = 'Escribe el nombre como aparece en la tarjeta.';
  if (!numero) errores.numero = 'El número de la tarjeta es obligatorio.';
  else if (numero.length !== largoNumero(marca) || !pasaLuhn(numero)) errores.numero = 'El número de la tarjeta no es válido.';
  if (!vencimientoValido(t.vencimiento)) errores.vencimiento = 'Usa el formato MM/AA y una fecha que no haya pasado.';
  if (soloDigitos(t.cvv).length !== largoCvv(marca)) errores.cvv = `El código de seguridad tiene ${largoCvv(marca)} dígitos.`;
  return errores;
}
