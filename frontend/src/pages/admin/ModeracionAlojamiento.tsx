import { useState } from 'react';
import { Ban, CircleAlert, CircleCheck, LoaderCircle, LockOpen, UserX, UserCheck } from 'lucide-react';
import { bloquearAlojamiento, desbloquearAlojamiento, levantarRestriccion, restringirUsuario } from '../../api/admin';
import { obtenerAlojamiento } from '../../api/alojamientos';
import { mensajeParaUsuario } from '../../api/mensajes';
import { FormularioMotivo } from '../../components/FormularioMotivo';
import type { Alojamiento } from '../../types/alojamiento';
import type { ResultadoModeracion } from '../../types/admin';

type Accion = 'bloquear' | 'restringir';

interface PropiedadesModeracion {
  alojamiento: Alojamiento;
  alCambiar: (alojamiento: Alojamiento) => void;
}

function resumir(resultado: ResultadoModeracion): string {
  const partes: string[] = [];
  if (resultado.reservasEnRevision > 0)
    partes.push(`${resultado.reservasEnRevision} ${resultado.reservasEnRevision === 1 ? 'reserva pasó' : 'reservas pasaron'} a revisión`);
  if (resultado.alojamientosPausados > 0)
    partes.push(`${resultado.alojamientosPausados} ${resultado.alojamientosPausados === 1 ? 'alojamiento quedó pausado' : 'alojamientos quedaron pausados'}`);
  return partes.length ? ` ${partes.join(' y ')}.` : '';
}

/**
 * Moderación desde el detalle de un alojamiento (MA-80, MA-81, RF-36, RF-37): bloquearlo por
 * información engañosa o desbloquearlo, y restringir a su anfitrión o levantar la restricción.
 */
export function ModeracionAlojamiento({ alojamiento: a, alCambiar }: PropiedadesModeracion) {
  const [abierto, setAbierto] = useState<Accion | null>(null);
  const [enCurso, setEnCurso] = useState<'desbloquear' | 'levantar' | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function recargar() {
    alCambiar(await obtenerAlojamiento(a.id));
  }

  async function bloquear(motivo: string) {
    const resultado = await bloquearAlojamiento(a.id, motivo);
    setAbierto(null);
    setAviso(`El alojamiento quedó bloqueado y salió de las búsquedas.${resumir(resultado)}`);
    await recargar();
  }

  async function restringir(motivo: string) {
    const resultado = await restringirUsuario(a.anfitrionId, motivo);
    setAbierto(null);
    setAviso(`${a.anfitrionNombre} quedó restringido: se cerraron sus sesiones.${resumir(resultado)}`);
    await recargar();
  }

  async function ejecutar(accion: 'desbloquear' | 'levantar') {
    setEnCurso(accion);
    setError(null);
    setAviso(null);
    try {
      if (accion === 'desbloquear') {
        await desbloquearAlojamiento(a.id);
        setAviso('El alojamiento se desbloqueó y quedó pausado: su anfitrión decide cuándo reanudarlo.');
      } else {
        await levantarRestriccion(a.anfitrionId);
        setAviso(`Se levantó la restricción de ${a.anfitrionNombre}. Puede volver a iniciar sesión.`);
      }
      await recargar();
    } catch (e) {
      setError(mensajeParaUsuario(e));
    } finally {
      setEnCurso(null);
    }
  }

  const ocupado = enCurso !== null || abierto !== null;

  return (
    <div className="detalle__moderacion">
      <h3 className="detalle__moderacion-titulo">Moderación</h3>

      {aviso && (
        <div className="alerta alerta--exito" role="status">
          <CircleCheck size={16} aria-hidden="true" />
          <span>{aviso}</span>
        </div>
      )}
      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      {abierto === 'bloquear' && (
        <FormularioMotivo
          etiqueta="Motivo del bloqueo"
          placeholder="Por ejemplo: las fotos no corresponden al alojamiento."
          ayuda="Sus reservas futuras pasan a revisión."
          textoConfirmar="Bloquear alojamiento"
          textoEnviando="Bloqueando…"
          alConfirmar={bloquear}
          alCancelar={() => setAbierto(null)}
        />
      )}
      {abierto === 'restringir' && (
        <FormularioMotivo
          etiqueta={`Motivo para restringir a ${a.anfitrionNombre}`}
          placeholder="Por ejemplo: publica información engañosa de forma reiterada."
          ayuda="No podrá iniciar sesión; sus alojamientos se pausan y sus reservas futuras pasan a revisión."
          textoConfirmar="Restringir anfitrión"
          textoEnviando="Restringiendo…"
          alConfirmar={restringir}
          alCancelar={() => setAbierto(null)}
        />
      )}

      {abierto === null && (
        <div className="detalle__botones">
          {a.estado === 'BLOQUEADA' ? (
            <button type="button" className="boton boton--secundario boton--bloque" onClick={() => ejecutar('desbloquear')} disabled={ocupado}>
              {enCurso === 'desbloquear' ? <LoaderCircle size={15} className="girando" aria-hidden="true" /> : <LockOpen size={15} aria-hidden="true" />}
              Desbloquear alojamiento
            </button>
          ) : (
            <button type="button" className="boton boton--peligro boton--bloque" onClick={() => setAbierto('bloquear')} disabled={ocupado}>
              <Ban size={15} aria-hidden="true" />
              Bloquear alojamiento
            </button>
          )}
          <button type="button" className="boton boton--peligro boton--bloque" onClick={() => setAbierto('restringir')} disabled={ocupado}>
            <UserX size={15} aria-hidden="true" />
            Restringir anfitrión
          </button>
          <button type="button" className="boton boton--secundario boton--bloque" onClick={() => ejecutar('levantar')} disabled={ocupado}>
            {enCurso === 'levantar' ? <LoaderCircle size={15} className="girando" aria-hidden="true" /> : <UserCheck size={15} aria-hidden="true" />}
            Levantar restricción del anfitrión
          </button>
        </div>
      )}
    </div>
  );
}
