import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { CircleAlert, Eye, EyeOff, House, LoaderCircle, Lock, Mail } from 'lucide-react';
import { ApiError } from '../../api/ApiError';
import { useAuth } from '../../auth/useAuth';
import { CampoTexto } from '../../components/CampoTexto';
import { Logo } from '../../components/Logo';
import { RUTAS } from '../../routes/rutas';
import './LoginPage.css';

interface ErroresCampos {
  email?: string;
  password?: string;
}

const FORMATO_CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validar(email: string, password: string): ErroresCampos {
  const errores: ErroresCampos = {};
  if (!email.trim()) errores.email = 'El correo es obligatorio.';
  else if (!FORMATO_CORREO.test(email.trim())) errores.email = 'El correo no tiene un formato válido.';
  if (!password) errores.password = 'La contraseña es obligatoria.';
  return errores;
}

/** Convierte el error del backend en mensajes para el formulario. */
function interpretarError(error: unknown): { campos: ErroresCampos; general: string | null } {
  if (!(error instanceof ApiError)) {
    return { campos: {}, general: 'Ocurrió un error inesperado. Intenta de nuevo.' };
  }
  if (error.codigo === 'DATOS_INVALIDOS') {
    return { campos: { email: error.errores.email, password: error.errores.password }, general: null };
  }
  // CREDENCIALES_INVALIDAS, CUENTA_RESTRINGIDA, CUENTA_DESACTIVADA y CUENTA_BLOQUEADA_TEMPORALMENTE
  // traen un mensaje pensado para el usuario. En errores del servidor se agrega el id de
  // correlación para poder rastrearlo en los logs (ADR-012).
  const seguimiento = error.status >= 500 && error.correlationId ? ` Código de seguimiento: ${error.correlationId}.` : '';
  return { campos: {}, general: error.message + seguimiento };
}

export default function LoginPage() {
  const { iniciarSesion } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [verPassword, setVerPassword] = useState(false);
  const [errores, setErrores] = useState<ErroresCampos>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function manejarEnvio(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (enviando) return;

    const validacion = validar(email, password);
    setErrores(validacion);
    setErrorGeneral(null);
    if (validacion.email || validacion.password) return;

    setEnviando(true);
    try {
      // Al guardar la sesión, la guardia SoloInvitados redirige a la página del rol.
      await iniciarSesion({ email: email.trim(), password });
    } catch (error) {
      const { campos, general } = interpretarError(error);
      setErrores(campos);
      setErrorGeneral(general);
      setEnviando(false);
    }
  }

  return (
    <div className="login">
      <aside className="login__panel">
        <Logo variante="claro" />
        <div className="login__panel-contenido">
          <h1 className="login__titulo-panel">Encuentra tu próxima estancia en la región.</h1>
          <p className="login__subtitulo-panel">
            Alojamientos temporales verificados, con disponibilidad en tiempo real y reservas sin duplicados.
          </p>
        </div>
        <div className="login__mosaico" aria-hidden="true">
          <span className="login__tesela login__tesela--1">
            <House size={22} strokeWidth={1.5} />
          </span>
          <span className="login__tesela login__tesela--2">
            <House size={22} strokeWidth={1.5} />
          </span>
          <span className="login__tesela login__tesela--3">
            <House size={22} strokeWidth={1.5} />
          </span>
        </div>
      </aside>

      <main className="login__zona-formulario">
        <div className="login__tarjeta">
          <h2 className="login__titulo">Bienvenido de nuevo</h2>
          <p className="login__instruccion">Ingresa con tu correo para continuar.</p>

          <form onSubmit={manejarEnvio} noValidate>
            {errorGeneral && (
              <div className="alerta alerta--error" role="alert">
                <CircleAlert size={16} aria-hidden="true" />
                <span>{errorGeneral}</span>
              </div>
            )}

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

            <CampoTexto
              etiqueta="Contraseña"
              icono={Lock}
              type={verPassword ? 'text' : 'password'}
              name="password"
              autoComplete="current-password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              error={errores.password}
              disabled={enviando}
              accesorio={
                <button
                  type="button"
                  className="campo__accesorio"
                  onClick={() => setVerPassword((v) => !v)}
                  aria-label={verPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  aria-pressed={verPassword}
                >
                  {verPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              }
            />

            <Link to={RUTAS.recuperarContrasena} className="enlace login__olvido">
              ¿Olvidaste tu contraseña?
            </Link>

            <button type="submit" className="boton boton--primario boton--bloque" disabled={enviando}>
              {enviando && <LoaderCircle size={16} className="girando" aria-hidden="true" />}
              {enviando ? 'Iniciando sesión…' : 'Iniciar sesión'}
            </button>
          </form>

          <p className="login__registro">
            ¿No tienes cuenta?{' '}
            <Link to={RUTAS.registro} className="enlace">
              Regístrate
            </Link>
          </p>
        </div>
      </main>
    </div>
  );
}
