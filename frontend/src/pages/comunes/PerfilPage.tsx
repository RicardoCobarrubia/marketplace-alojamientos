import { useEffect, useState, type FormEvent } from 'react';
import { CircleAlert, CircleCheck, LoaderCircle, Lock, Mail, UserRound, UserX } from 'lucide-react';
import { ApiError } from '../../api/ApiError';
import { mensajeParaUsuario } from '../../api/mensajes';
import { actualizarMiPerfil, desactivarMiCuenta, obtenerMiPerfil } from '../../api/perfil';
import { dejarAvisoLogin } from '../../auth/sesion';
import { useAuth } from '../../auth/useAuth';
import { CampoTexto } from '../../components/CampoTexto';
import { NOMBRE_ROL, type Usuario } from '../../types/auth';
import { FORMATO_TELEFONO, MENSAJE_TELEFONO } from '../../utils/validaciones';
import './PerfilPage.css';

type Errores = { nombre?: string; telefono?: string };

/** Mismas reglas que UsuarioDTO (grupo Perfil) en el backend. */
function validar(nombre: string, telefono: string): Errores {
  const errores: Errores = {};
  if (!nombre) errores.nombre = 'El nombre es obligatorio.';
  else if (nombre.length > 100) errores.nombre = 'El nombre admite máximo 100 caracteres.';
  if (!telefono) errores.telefono = 'El teléfono es obligatorio.';
  else if (!FORMATO_TELEFONO.test(telefono)) errores.telefono = MENSAJE_TELEFONO;
  return errores;
}

/**
 * Mi perfil, para cualquier rol (MA-51, MA-52, RF-05, RF-06): consultar los datos, cambiar nombre
 * y teléfono (el correo y el rol no se editan) y desactivar la cuenta.
 */
export default function PerfilPage() {
  const { usuario, actualizarUsuario, cerrarSesion } = useAuth();
  const [perfil, setPerfil] = useState<Usuario | null>(usuario);
  const [nombre, setNombre] = useState(usuario?.nombre ?? '');
  const [telefono, setTelefono] = useState(usuario?.telefono ?? '');
  const [errores, setErrores] = useState<Errores>({});
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  // Se traen los datos frescos del backend: la sesión guarda los del momento del login.
  useEffect(() => {
    let activo = true;
    obtenerMiPerfil()
      .then((datos) => {
        if (!activo) return;
        setPerfil(datos);
        setNombre(datos.nombre);
        setTelefono(datos.telefono);
      })
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, []);

  const sinCambios = perfil !== null && nombre.trim() === perfil.nombre && telefono.trim() === perfil.telefono;

  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    const datos = { nombre: nombre.trim(), telefono: telefono.trim() };
    const nuevos = validar(datos.nombre, datos.telefono);
    setErrores(nuevos);
    setError(null);
    setAviso(null);
    if (nuevos.nombre || nuevos.telefono) return;

    setGuardando(true);
    try {
      const actualizado = await actualizarMiPerfil(datos);
      setPerfil(actualizado);
      setNombre(actualizado.nombre);
      setTelefono(actualizado.telefono);
      actualizarUsuario(actualizado); // la barra superior muestra el nombre nuevo
      setAviso('Tus datos se actualizaron.');
    } catch (e) {
      if (e instanceof ApiError && e.codigo === 'DATOS_INVALIDOS') {
        setErrores({ nombre: e.errores.nombre, telefono: e.errores.telefono });
      } else {
        setError(mensajeParaUsuario(e));
      }
    } finally {
      setGuardando(false);
    }
  }

  if (!perfil) {
    return error ? (
      <div className="alerta alerta--error" role="alert">
        <CircleAlert size={16} aria-hidden="true" />
        <span>{error}</span>
      </div>
    ) : (
      <div className="cargando">
        <LoaderCircle size={18} className="girando" aria-hidden="true" />
        Cargando tu perfil…
      </div>
    );
  }

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Mi perfil</h1>
          <p>{NOMBRE_ROL[perfil.rol]}</p>
        </div>
      </div>

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

      <div className="perfil">
        <form className="tarjeta tarjeta--relleno" onSubmit={guardar} noValidate>
          <h2 className="tarjeta__titulo">Datos personales</h2>
          <CampoTexto
            etiqueta="Nombre completo"
            icono={UserRound}
            autoComplete="name"
            maxLength={100}
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            error={errores.nombre}
            disabled={guardando}
          />
          <CampoTexto
            etiqueta="Teléfono"
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            maxLength={20}
            value={telefono}
            onChange={(e) => setTelefono(e.target.value)}
            error={errores.telefono}
            disabled={guardando}
          />
          <CampoTexto
            etiqueta="Correo electrónico"
            icono={Mail}
            value={perfil.email}
            readOnly
            disabled
            ayuda="El correo y el rol no se pueden cambiar."
            accesorio={<Lock size={14} className="campo__icono" aria-hidden="true" />}
          />
          <div className="perfil__botones">
            <button type="submit" className="boton boton--primario" disabled={guardando || sinCambios}>
              {guardando && <LoaderCircle size={16} className="girando" aria-hidden="true" />}
              {guardando ? 'Guardando…' : 'Guardar cambios'}
            </button>
          </div>
        </form>

        {perfil.rol !== 'ADMINISTRADOR' && (
          <ZonaDesactivar
            esAnfitrion={perfil.rol === 'ANFITRION'}
            alDesactivar={() => {
              dejarAvisoLogin('Tu cuenta quedó desactivada. Si vuelves a iniciar sesión podrás reactivarla.');
              cerrarSesion();
            }}
          />
        )}
      </div>
    </>
  );
}

interface PropiedadesZona {
  esAnfitrion: boolean;
  alDesactivar: () => void;
}

/** Desactivar la cuenta (RF-06): se pide confirmar antes, porque cierra la sesión. */
function ZonaDesactivar({ esAnfitrion, alDesactivar }: PropiedadesZona) {
  const [confirmando, setConfirmando] = useState(false);
  const [desactivando, setDesactivando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function desactivar() {
    setDesactivando(true);
    setError(null);
    try {
      await desactivarMiCuenta();
      alDesactivar();
    } catch (e) {
      setError(mensajeParaUsuario(e));
      setDesactivando(false);
    }
  }

  return (
    <section className="tarjeta tarjeta--relleno perfil__peligro">
      <h2 className="tarjeta__titulo">Desactivar cuenta</h2>
      <ul className="perfil__consecuencias">
        <li>Se cierran tus sesiones abiertas.</li>
        {esAnfitrion && <li>Tus alojamientos activos quedan en pausa y salen de las búsquedas.</li>}
        <li>Puedes reactivarla cuando quieras iniciando sesión con tu correo y contraseña.</li>
        <li>No es posible si tienes reservas confirmadas que aún no terminan.</li>
      </ul>

      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      {confirmando ? (
        <div className="perfil__confirmacion">
          <p>¿Seguro que quieres desactivar tu cuenta?</p>
          <div className="perfil__botones">
            <button type="button" className="boton boton--secundario" onClick={() => setConfirmando(false)} disabled={desactivando}>
              Cancelar
            </button>
            <button type="button" className="boton boton--peligro" onClick={desactivar} disabled={desactivando}>
              {desactivando && <LoaderCircle size={16} className="girando" aria-hidden="true" />}
              {desactivando ? 'Desactivando…' : 'Sí, desactivar'}
            </button>
          </div>
        </div>
      ) : (
        <button type="button" className="boton boton--peligro" onClick={() => setConfirmando(true)}>
          <UserX size={16} aria-hidden="true" />
          Desactivar mi cuenta
        </button>
      )}
    </section>
  );
}
