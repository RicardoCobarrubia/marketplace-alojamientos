import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { CircleAlert, LoaderCircle, Lock, Mail } from 'lucide-react';
import { ApiError } from '../../api/ApiError';
import { mensajeParaUsuario } from '../../api/mensajes';
import { useAuth } from '../../auth/useAuth';
import { BotonVerContrasena } from '../../components/BotonVerContrasena';
import { CampoTexto } from '../../components/CampoTexto';
import { LayoutAutenticacion } from '../../components/LayoutAutenticacion';
import { RUTAS } from '../../routes/rutas';
import { FORMATO_CORREO } from '../../utils/validaciones';
import './LoginPage.css';

interface ErroresCampos {
  email?: string;
  password?: string;
}

function validar(email: string, password: string): ErroresCampos {
  const errores: ErroresCampos = {};
  if (!email.trim()) errores.email = 'El correo es obligatorio.';
  else if (!FORMATO_CORREO.test(email.trim())) errores.email = 'El correo no tiene un formato válido.';
  if (!password) errores.password = 'La contraseña es obligatoria.';
  return errores;
}

/** Convierte el error del backend en mensajes para el formulario. */
function interpretarError(error: unknown): { campos: ErroresCampos; general: string | null } {
  if (error instanceof ApiError && error.codigo === 'DATOS_INVALIDOS') {
    return { campos: { email: error.errores.email, password: error.errores.password }, general: null };
  }
  // CREDENCIALES_INVALIDAS, CUENTA_RESTRINGIDA, CUENTA_DESACTIVADA y CUENTA_BLOQUEADA_TEMPORALMENTE
  // traen un mensaje pensado para el usuario.
  return { campos: {}, general: mensajeParaUsuario(error) };
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
    <LayoutAutenticacion>
      <h2 className="autenticacion__titulo">Bienvenido de nuevo</h2>
      <p className="autenticacion__instruccion">Ingresa con tu correo para continuar.</p>

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
          accesorio={<BotonVerContrasena visible={verPassword} alCambiar={() => setVerPassword((v) => !v)} />}
        />

        <Link to={RUTAS.recuperarContrasena} className="enlace login__olvido">
          ¿Olvidaste tu contraseña?
        </Link>

        <button type="submit" className="boton boton--primario boton--bloque" disabled={enviando}>
          {enviando && <LoaderCircle size={16} className="girando" aria-hidden="true" />}
          {enviando ? 'Iniciando sesión…' : 'Iniciar sesión'}
        </button>
      </form>

      <p className="autenticacion__pie">
        ¿No tienes cuenta?{' '}
        <Link to={RUTAS.registro} className="enlace">
          Regístrate
        </Link>
      </p>
    </LayoutAutenticacion>
  );
}
