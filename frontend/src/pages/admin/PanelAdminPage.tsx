import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { ChevronRight, ListChecks } from 'lucide-react';
import { listarAlojamientosAdmin } from '../../api/admin';
import { RUTAS } from '../../routes/rutas';
import type { Alojamiento } from '../../types/alojamiento';
import './PanelAdminPage.css';

/**
 * Panel de administración. Las métricas del mockup (usuarios, ingresos, reservas por estado)
 * necesitan endpoints que el backend aún no tiene (RF-39, RF-40); por ahora se muestran los
 * datos de alojamientos que sí existen.
 */
export default function PanelAdminPage() {
  const [alojamientos, setAlojamientos] = useState<Alojamiento[] | null>(null);

  useEffect(() => {
    let activo = true;
    listarAlojamientosAdmin()
      .then((lista) => activo && setAlojamientos(lista))
      .catch(() => activo && setAlojamientos([]));
    return () => {
      activo = false;
    };
  }, []);

  const contar = (estado: Alojamiento['estado']) => alojamientos?.filter((a) => a.estado === estado).length ?? 0;
  const pendientes = contar('PENDIENTE_REVISION');

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Panel de administración</h1>
          <p>Resumen de la operación</p>
        </div>
      </div>

      <div className="panel__metricas">
        <Metrica titulo="Alojamientos activos" valor={alojamientos ? contar('ACTIVA') : '…'} detalle={`${alojamientos?.length ?? 0} en total`} />
        <Metrica titulo="Esperan revisión" valor={alojamientos ? pendientes : '…'} detalle="Publicaciones enviadas" destacada={pendientes > 0} />
        <Metrica titulo="Pausados" valor={alojamientos ? contar('PAUSADA') : '…'} detalle="Por sus anfitriones" />
        <Metrica titulo="Bloqueados" valor={alojamientos ? contar('BLOQUEADA') : '…'} detalle="Por moderación" />
      </div>

      <section className="tarjeta tarjeta--relleno">
        <h2 className="tarjeta__titulo">Pendientes de atención</h2>
        {pendientes > 0 ? (
          <Link to={RUTAS.adminPublicaciones} className="panel__pendiente">
            <ListChecks size={16} aria-hidden="true" />
            {pendientes} {pendientes === 1 ? 'publicación espera' : 'publicaciones esperan'} revisión
            <ChevronRight size={16} aria-hidden="true" className="panel__flecha" />
          </Link>
        ) : (
          <p className="campo__ayuda">No hay publicaciones esperando revisión.</p>
        )}
        <p className="campo__ayuda panel__nota">
          Las métricas de usuarios, reservas e ingresos se agregan cuando el backend tenga esos módulos.
        </p>
      </section>
    </>
  );
}

interface PropiedadesMetrica {
  titulo: string;
  valor: number | string;
  detalle: string;
  destacada?: boolean;
}

function Metrica({ titulo, valor, detalle, destacada }: PropiedadesMetrica) {
  return (
    <div className="tarjeta panel__metrica">
      <span className="panel__metrica-titulo">{titulo}</span>
      <span className={destacada ? 'panel__metrica-valor panel__metrica-valor--destacada' : 'panel__metrica-valor'}>{valor}</span>
      <span className="panel__metrica-detalle">{detalle}</span>
    </div>
  );
}
