import { LayoutApp, type ItemNavegacion } from '../../components/LayoutApp';
import { RUTAS } from '../../routes/rutas';

const NAVEGACION: ItemNavegacion[] = [{ etiqueta: 'Mis reservas', ruta: RUTAS.huesped }];

export default function LayoutHuesped() {
  return <LayoutApp navegacion={NAVEGACION} />;
}
