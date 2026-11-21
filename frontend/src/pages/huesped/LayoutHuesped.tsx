import { LayoutApp, type ItemNavegacion } from '../../components/LayoutApp';
import { RUTAS } from '../../routes/rutas';

const NAVEGACION: ItemNavegacion[] = [
  { etiqueta: 'Explorar', ruta: RUTAS.huesped, exacta: true },
  { etiqueta: 'Mis reservas', ruta: RUTAS.misReservas },
];

export default function LayoutHuesped() {
  return <LayoutApp navegacion={NAVEGACION} />;
}
