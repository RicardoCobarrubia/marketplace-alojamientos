import { Link } from 'react-router';
import { Plus } from 'lucide-react';
import { LayoutApp, type ItemNavegacion } from '../../components/LayoutApp';
import { RUTAS } from '../../routes/rutas';

const NAVEGACION: ItemNavegacion[] = [
  { etiqueta: 'Mis alojamientos', ruta: RUTAS.anfitrion, exacta: true },
  { etiqueta: 'Reservas recibidas', ruta: RUTAS.reservasRecibidas },
  { etiqueta: 'Disponibilidad', ruta: RUTAS.disponibilidad },
];

export default function LayoutAnfitrion() {
  return (
    <LayoutApp
      navegacion={NAVEGACION}
      accion={
        <Link to={RUTAS.nuevoAlojamiento} className="boton boton--primario boton--pequeno layout-anfitrion__nuevo">
          <Plus size={15} aria-hidden="true" />
          Nuevo alojamiento
        </Link>
      }
    />
  );
}
