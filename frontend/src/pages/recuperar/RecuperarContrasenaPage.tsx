import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { ArrowLeft, CircleAlert, KeyRound, LoaderCircle, Lock, Mail, MailCheck } from 'lucide-react';
import { restablecerContrasena, solicitarCodigoRecuperacion } from '../../api/auth';
import { ApiError } from '../../api/ApiError';
import { mensajeParaUsuario } from '../../api/mensajes';
import { dejarAvisoLogin } from '../../auth/sesion';
import { BotonVerContrasena } from '../../components/BotonVerContrasena';
import { CampoTexto } from '../../components/CampoTexto';
import { LayoutAutenticacion } from '../../components/LayoutAutenticacion';
import { RUTAS } from '../../routes/rutas';
import { bytesUtf8, FORMATO_CORREO, MAXIMO_BYTES_CONTRASENA } from '../../utils/validaciones';
import './RecuperarContrasenaPage.css';

/** El backend no envía otro código antes de 60 segundos (EmisionCodigos.SEGUNDOS_ENTRE_CODIGOS). */
const ESPERA_REENVIO = 60;

interface ErroresCampos {
  email?: string;
  codigo?: string;
  password?: string;
  confirmacion?: string;
}

function validarCorreo(email: string): string | undefined {
  if (!email.trim()) return 'El correo es obligatorio.';
  if (!FORMATO_CORREO.test(email.trim())) return 'El correo no tiene un formato válido.';
  return undefined;
}

/** Mismas reglas que NuevaContrasenaDTO y el registro (MA-47, MA-50). */
function validarCambio(codigo: string, password: string, confirmacion: string): ErroresCampos {
  const errores: ErroresCampos = {};
  if (!/^\d{6}$/.test(codigo)) errores.codigo = 'El código tiene 6 dígitos.';
  if (!password) errores.password = 'La contraseña es obligatoria.';
  else if (password.length < 8 || password.length > 72)
    errores.password = 'La contraseña debe tener entre 8 y 72 caracteres.';
  else if (bytesUtf8(password) > MAXIMO_BYTES_CONTRASENA)
    errores.password = 'La contraseña es demasiado larga; usa menos caracteres especiales.';
  if (!errores.password && confirmacion !== password) errores.confirmacion = 'Las contraseñas no coinciden.';
  return errores;
}

/**
 * Recuperar la contraseña (MA-50, RF-04) en dos pasos: pedir un código al correo y luego
 * escribirlo junto con la nueva contraseña. Al terminar vuelve al login con un aviso.
 */
export default function RecuperarContrasenaPage() {
  const navegar = useNavigate();
  // El login pasa el correo que el usuario ya había escrito
  const { state } = useLocation() as { state: { email?: string } | null };
  const [paso, setPaso] = useState<'correo' | 'codigo'>('correo');
  const [email, setEmail] = useState(state?.email ?? '');
  const [codigo, setCodigo] = useState('');
  const [password, setPassword] = useState('');
  const [confirmacion, setConfirmacion] = useState('');
  const [verPassword, setVerPassword] = useState(false);
  const [errores, setErrores] = useState<ErroresCampos>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [mensajeEnvio, setMensajeEnvio] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [segundosReenvio, setSegundosReenvio] = useState(0);

  useEffect(() => {
    if (segundosReenvio <= 0) return;
    const temporizador = setTimeout(() => setSegundosReenvio((s) => s - 1), 1000);
    return () => clearTimeout(temporizador);
  }, [segundosReenvio]);

  async function pedirCodigo() {
    setEnviando(true);
    setErrorGeneral(null);
    try {
      const { mensaje } = await solicitarCodigoRecuperacion(email.trim());
      setMensajeEnvio(mensaje);
      setPaso('codigo');
      setSegundosReenvio(ESPERA_REENVIO);
    } catch (error) {
      if (error instanceof ApiError && error.codigo === 'DATOS_INVALIDOS' && error.errores.email) {
        setErrores({ email: error.errores.email });
      } else {
        setErrorGeneral(mensajeParaUsuario(error));
      }
    } finally {
      setEnviando(false);
    }
  }

  async function enviarCorreo(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (enviando) return;
    const errorEmail = validarCorreo(email);
    setErrores({ email: errorEmail });
    if (!errorEmail) await pedirCodigo();
  }

  async function enviarCambio(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (enviando) return;
    const validacion = validarCambio(codigo, password, confirmacion);
    setErrores(validacion);
    setErrorGeneral(null);
    if (Object.values(validacion).some(Boolean)) return;

    setEnviando(true);
    try {
      await restablecerContrasena(email.trim(), codigo, password);
      dejarAvisoLogin('Tu contraseña se cambió. Inicia sesión con la nueva.');
      navegar(RUTAS.login, { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.codigo === 'CODIGO_INVALIDO') {
        setErrores({ codigo: error.message });
      } else if (error instanceof ApiError && error.codigo === 'CONTRASENA_MUY_LARGA') {
        setErrores({ password: error.message });
      } else if (error instanceof ApiError && error.codigo === 'DATOS_INVALIDOS') {
        setErrores({ codigo: error.errores.codigo, password: error.errores.password });
      } else {
        setErrorGeneral(mensajeParaUsuario(error));
      }
      setEnviando(false);
    }
  }

  function usarOtroCorreo() {
    setPaso('correo');
    setCodigo('');
    setPassword('');
    setConfirmacion('');
    setErrores({});
    setErrorGeneral(null);
    setMensajeEnvio(null);
  }

  const alertaError = errorGeneral && (
    <div className="alerta alerta--error" role="alert">
      <CircleAlert size={16} aria-hidden="true" />
      <span>{errorGeneral}</span>
    </div>
  );

  return (
    <LayoutAutenticacion>
      <Link to={RUTAS.login} className="enlace recuperar__volver">
        <ArrowLeft size={14} aria-hidden="true" />
        Volver a iniciar sesión
      </Link>

      {paso === 'correo' ? (
        <>
          <h2 className="autenticacion__titulo">Recupera tu contraseña</h2>
          <p className="autenticacion__instruccion">
            Escribe el correo de tu cuenta y te enviaremos un código de 6 dígitos para crear una contraseña nueva.
          </p>
          <form onSubmit={enviarCorreo} noValidate>
            {alertaError}
            <CampoTexto
              etiqueta="Correo electrónico"
              icono={Mail}
              type="email"
              name="email"
              autoComplete="email"
              placeholder="tu@correo.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={errores.email}
              disabled={enviando}
              autoFocus
            />
            <button type="submit" className="boton boton--primario boton--bloque" disabled={enviando}>
              {enviando && <LoaderCircle size={16} className="girando" aria-hidden="true" />}
              {enviando ? 'Enviando…' : 'Enviar código'}
            </button>
          </form>
        </>
      ) : (
        <>
          <h2 className="autenticacion__titulo">Crea tu nueva contraseña</h2>
          <div className="alerta alerta--info" role="status">
            <MailCheck size={16} aria-hidden="true" />
            <span>
              {mensajeEnvio} Revisa también la carpeta de spam. Correo: <strong>{email.trim()}</strong>
            </span>
          </div>
          <form onSubmit={enviarCambio} noValidate>
            {alertaError}
            <CampoTexto
              etiqueta="Código"
              icono={KeyRound}
              name="codigo"
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder="123456"
              maxLength={6}
              value={codigo}
              onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ''))}
              error={errores.codigo}
              disabled={enviando}
              autoFocus
            />
            <CampoTexto
              etiqueta="Nueva contraseña"
              icono={Lock}
              type={verPassword ? 'text' : 'password'}
              name="password"
              autoComplete="new-password"
              placeholder="Mínimo 8 caracteres"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              error={errores.password}
              disabled={enviando}
              accesorio={<BotonVerContrasena visible={verPassword} alCambiar={() => setVerPassword((v) => !v)} />}
            />
            <CampoTexto
              etiqueta="Confirma la contraseña"
              icono={Lock}
              type={verPassword ? 'text' : 'password'}
              name="confirmacion"
              autoComplete="new-password"
              placeholder="Escríbela de nuevo"
              value={confirmacion}
              onChange={(e) => setConfirmacion(e.target.value)}
              error={errores.confirmacion}
              disabled={enviando}
            />
            <button type="submit" className="boton boton--primario boton--bloque" disabled={enviando}>
              {enviando && <LoaderCircle size={16} className="girando" aria-hidden="true" />}
              {enviando ? 'Guardando…' : 'Cambiar contraseña'}
            </button>
          </form>

          <div className="recuperar__acciones">
            <button
              type="button"
              className="enlace-accion"
              onClick={pedirCodigo}
              disabled={enviando || segundosReenvio > 0}
            >
              {segundosReenvio > 0 ? `Reenviar código en ${segundosReenvio} s` : 'Reenviar código'}
            </button>
            <button type="button" className="enlace-accion" onClick={usarOtroCorreo} disabled={enviando}>
              Usar otro correo
            </button>
          </div>
          <p className="campo__ayuda recuperar__nota">
            El código vence en 15 minutos y sirve una sola vez. Al cambiar la contraseña se cierran las sesiones que
            tengas abiertas.
          </p>
        </>
      )}
    </LayoutAutenticacion>
  );
}
