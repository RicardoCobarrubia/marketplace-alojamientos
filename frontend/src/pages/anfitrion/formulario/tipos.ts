import type { Catalogos } from '../../../hooks/useCatalogos';
import type { AdicionalLocal, CampoFormulario, Errores, Formulario } from './modelo';

/** Propiedades comunes de los pasos del formulario. */
export interface PropiedadesPaso {
  formulario: Formulario;
  errores: Errores;
  catalogos: Catalogos;
  deshabilitado: boolean;
  cambiar: <C extends CampoFormulario>(campo: C, valor: Formulario[C]) => void;
  cambiarAdicionales: (adicionales: AdicionalLocal[]) => void;
}
