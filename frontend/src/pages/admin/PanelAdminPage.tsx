import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { ChevronRight, CircleAlert, ListChecks, LoaderCircle, TriangleAlert } from 'lucide-react';
import { listarAlojamientosAdmin, obtenerIndicadores, obtenerIngresos } from '../../api/admin';
import { mensajeParaUsuario } from '../../api/mensajes';
import { CampoSeleccion } from '../../components/CampoSeleccion';
import { RUTAS } from '../../routes/rutas';
import type { Indicadores, Ingresos } from '../../types/admin';
import { NOMBRE_ESTADO_RESERVA, type EstadoReserva } from '../../types/reserva';
import { aIso, formatearFecha, formatearPrecio } from '../../utils/formato';
import './PanelAdminPage.css';

const RANGOS = [
  { dias: 7, etiqueta: 'Últimos 7 días' },
  { dias: 30, etiqueta: 'Últimos 30 días' },
  { dias: 90, etiqueta: 'Últimos 90 días' },
  { dias: 365, etiqueta: 'Último año' },
];

const ORDEN_ESTADOS: EstadoReserva[] = ['CONFIRMADA', 'COMPLETADA', 'CANCELADA', 'PAGO_FALLIDO', 'EN_REVISION', 'PENDIENTE_PAGO'];

const NOMBRE_ROL: Record<string, [singular: string, plural: string]> = {
  HUESPED: ['huésped', 'huéspedes'],
  ANFITRION: ['anfitrión', 'anfitriones'],
  ADMINISTRADOR: ['administrador', 'administradores'],
};

function nombreRol(rol: string, cantidad: number): string {
  const [singular, plural] = NOMBRE_ROL[rol] ?? [rol.toLowerCase(), rol.toLowerCase()];
  return cantidad === 1 ? singular : plural;
}

function rangoDesdeHoy(dias: number): { desde: string; hasta: string } {
  const hoy = new Date();
  const desde = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() - (dias - 1));
  return { desde: aIso(desde), hasta: aIso(hoy) };
}

/** Panel de administración con los indicadores de la plataforma (MA-83, MA-84, RF-39, RF-40). */
export default function PanelAdminPage() {
  const [dias, setDias] = useState(30);
  const [indicadores, setIndicadores] = useState<Indicadores | null>(null);
  const [ingresos, setIngresos] = useState<Ingresos | null>(null);
  const [pendientes, setPendientes] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let activo = true;
    Promise.all([obtenerIndicadores(), listarAlojamientosAdmin('PENDIENTE_REVISION')])
      .then(([datos, lista]) => {
        if (!activo) return;
        setIndicadores(datos);
        setPendientes(lista.length);
      })
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, []);

  useEffect(() => {
    let activo = true;
    setIngresos(null);
    const { desde, hasta } = rangoDesdeHoy(dias);
    obtenerIngresos(desde, hasta)
      .then((datos) => activo && setIngresos(datos))
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, [dias]);

  const enRevision = indicadores?.reservasPorEstado.EN_REVISION ?? 0;
  const maximoEstado = Math.max(1, ...ORDEN_ESTADOS.map((e) => indicadores?.reservasPorEstado[e] ?? 0));
  const maximoCiudad = Math.max(1, ...(ingresos?.ciudadesConMasReservas.map((c) => c.reservas) ?? [0]));
  const detalleRoles = indicadores
    ? Object.entries(indicadores.usuariosPorRol)
        .filter(([rol]) => rol !== 'ADMINISTRADOR')
        .map(([rol, cantidad]) => `${cantidad} ${nombreRol(rol, cantidad)}`)
        .join(' · ')
    : '';

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Panel de administración</h1>
          <p>
            Resumen de la operación
            {ingresos && ` · ${formatearFecha(ingresos.desde)} – ${formatearFecha(ingresos.hasta)}`}
          </p>
        </div>
        <div className="panel__rango">
          <CampoSeleccion
            etiqueta="Periodo de ingresos"
            opciones={RANGOS.map((r) => ({ valor: r.dias, etiqueta: r.etiqueta }))}
            value={dias}
            onChange={(e) => setDias(Number(e.target.value))}
          />
        </div>
      </div>

      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      <div className="panel__metricas">
        <Metrica titulo="Usuarios registrados" valor={indicadores?.usuarios} detalle={detalleRoles} />
        <Metrica
          titulo="Alojamientos activos"
          valor={indicadores?.alojamientosActivos}
          detalle={pendientes === null ? '' : `${pendientes} pendientes de revisión`}
        />
        <Metrica
          titulo="Ingresos estimados"
          valor={ingresos ? formatearPrecio(ingresos.ingresosEstimados) : undefined}
          detalle="Tarifas de servicio confirmadas y completadas"
        />
        <Metrica
          titulo="Reservas en revisión"
          valor={indicadores ? enRevision : undefined}
          detalle={enRevision > 0 ? 'Requieren decisión' : 'Sin pendientes'}
          destacada={enRevision > 0}
        />
      </div>

      <div className="panel__graficas">
        <section className="tarjeta tarjeta--relleno">
          <h2 className="tarjeta__titulo">Reservas por estado</h2>
          {!indicadores ? (
            <Cargando />
          ) : (
            <ul className="barras" aria-label="Reservas por estado">
              {ORDEN_ESTADOS.map((estado) => {
                const cantidad = indicadores.reservasPorEstado[estado] ?? 0;
                return (
                  <li key={estado} className="barras__fila">
                    <span className="barras__etiqueta">{NOMBRE_ESTADO_RESERVA[estado]}</span>
                    <span className="barras__pista">
                      <span
                        className={`barras__relleno barras__relleno--${estado.toLowerCase()}`}
                        style={{ width: `${(cantidad / maximoEstado) * 100}%` }}
                      />
                    </span>
                    <span className="barras__valor">{cantidad}</span>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        <section className="tarjeta tarjeta--relleno">
          <h2 className="tarjeta__titulo">Ciudades con más reservas</h2>
          {!ingresos ? (
            <Cargando />
          ) : ingresos.ciudadesConMasReservas.length === 0 ? (
            <p className="campo__ayuda">No hay reservas en este periodo.</p>
          ) : (
            <ul className="barras" aria-label="Ciudades con más reservas">
              {ingresos.ciudadesConMasReservas.map((c) => (
                <li key={c.ciudadId} className="barras__fila">
                  <span className="barras__etiqueta">{c.ciudad}</span>
                  <span className="barras__pista">
                    <span className="barras__relleno" style={{ width: `${(c.reservas / maximoCiudad) * 100}%` }} />
                  </span>
                  <span className="barras__valor">{c.reservas}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="tarjeta tarjeta--relleno">
        <h2 className="tarjeta__titulo">Pendientes de atención</h2>
        {pendientes === 0 && enRevision === 0 && <p className="campo__ayuda">No hay nada pendiente. 🎉</p>}
        <div className="panel__pendientes">
          {!!pendientes && (
            <Link to={RUTAS.adminPublicaciones} className="panel__pendiente">
              <ListChecks size={16} aria-hidden="true" />
              {pendientes} {pendientes === 1 ? 'publicación espera' : 'publicaciones esperan'} revisión
              <ChevronRight size={16} aria-hidden="true" className="panel__flecha" />
            </Link>
          )}
          {enRevision > 0 && (
            <Link to={RUTAS.adminReservasRevision} className="panel__pendiente">
              <TriangleAlert size={16} aria-hidden="true" />
              {enRevision} {enRevision === 1 ? 'reserva en revisión' : 'reservas en revisión'} por bloqueos o restricciones
              <ChevronRight size={16} aria-hidden="true" className="panel__flecha" />
            </Link>
          )}
        </div>
      </section>
    </>
  );
}

function Cargando() {
  return (
    <div className="cargando cargando--compacto">
      <LoaderCircle size={16} className="girando" aria-hidden="true" />
    </div>
  );
}

interface PropiedadesMetrica {
  titulo: string;
  valor: number | string | undefined;
  detalle: string;
  destacada?: boolean;
}

function Metrica({ titulo, valor, detalle, destacada }: PropiedadesMetrica) {
  return (
    <div className="tarjeta panel__metrica">
      <span className="panel__metrica-titulo">{titulo}</span>
      <span className={destacada ? 'panel__metrica-valor panel__metrica-valor--destacada' : 'panel__metrica-valor'}>
        {valor === undefined ? '…' : typeof valor === 'number' ? valor.toLocaleString('es-CO') : valor}
      </span>
      <span className="panel__metrica-detalle">{detalle}</span>
    </div>
  );
}
