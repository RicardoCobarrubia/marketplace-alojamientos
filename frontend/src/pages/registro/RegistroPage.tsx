import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { CircleAlert, LoaderCircle, Lock, Mail, UserRound } from 'lucide-react';
import { registrarse } from '../../api/auth';
import { ApiError } from '../../api/ApiError';
import { mensajeParaUsuario } from '../../api/mensajes';
import { useAuth } from '../../auth/useAuth';
import { BotonVerContrasena } from '../../components/BotonVerContrasena';
import { CampoTexto } from '../../components/CampoTexto';
import { LayoutAutenticacion } from '../../components/LayoutAutenticacion';
import { RUTAS } from '../../routes/rutas';
import type { DatosRegistro, RolRegistro } from '../../types/auth';
import { bytesUtf8, FORMATO_CORREO, FORMATO_TELEFONO, MAXIMO_BYTES_CONTRASENA } from '../../utils/validaciones';
import './RegistroPage.css';

type CampoFormulario = 'nombre' | 'email' | 'telefono' | 'password';
type ErroresCampos = Partial<Record<CampoFormulario, string>>;

const OPCIONES_ROL: { valor: RolRegistro; titulo: string; descripcion: string }[] = [
  { valor: 'HUESPED', titulo: 'Huésped', descripcion: 'Busco y reservo alojamientos' },
  { valor: 'ANFITRION', titulo: 'Anfitrión', descripcion: 'Publico y administro alojamientos' },
];

/** Mismas reglas que UsuarioDTO (grupo Registro) en el backend. */
function validar(datos: DatosRegistro): ErroresCampos {
  const errores: ErroresCampos = {};

  if (!datos.nombre) errores.nombre = 'El nombre es obligatorio.';
  else if (datos.nombre.length > 100) errores.nombre = 'El nombre admite máximo 100 caracteres.';

  if (!datos.email) errores.email = 'El correo es obligatorio.';
  else if (datos.email.length > 254) errores.email = 'El correo admite máximo 254 caracteres.';
  else if (!FORMATO_CORREO.test(datos.email)) errores.email = 'El correo no tiene un formato válido.';

  if (!datos.telefono) errores.telefono = 'El teléfono es obligatorio.';
  else if (!FORMATO_TELEFONO.test(datos.telefono)) errores.telefono = 'El teléfono debe tener entre 7 y 20 dígitos.';

  if (!datos.password) errores.password = 'La contraseña es obligatoria.';
  else if (datos.password.length < 8 || datos.password.length > 72)
    errores.password = 'La contraseña debe tener entre 8 y 72 caracteres.';
  else if (bytesUtf8(datos.password) > MAXIMO_BYTES_CONTRASENA)
    errores.password = 'La contraseña es demasiado larga; usa menos caracteres especiales.';

  return errores;
}

/** Convierte el error del backend en mensajes por campo o en un mensaje general. */
function interpretarError(error: unknown): { campos: ErroresCampos; general: string | null } {
  if (error instanceof ApiError) {
    switch (error.codigo) {
      case 'CORREO_YA_REGISTRADO':
        return { campos: { email: error.message }, general: null };
      case 'CONTRASENA_MUY_LARGA':
        return { campos: { password: error.message }, general: null };
      case 'DATOS_INVALIDOS': {
        const { nombre, email, telefono, password, rol } = error.errores;
        return { campos: { nombre, email, telefono, password }, general: rol ?? null };
      }
    }
  }
  return { campos: {}, general: mensajeParaUsuario(error) };
}

export default function RegistroPage() {
  const { iniciarSesion } = useAuth();
  const [rol, setRol] = useState<RolRegistro>('HUESPED');
  const [nombre, setNombre] = useState('');
  const [email, setEmail] = useState('');
  const [telefono, setTelefono] = useState('');
  const [password, setPassword] = useState('');
  const [verPassword, setVerPassword] = useState(false);
  const [errores, setErrores] = useState<ErroresCampos>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function manejarEnvio(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (enviando) return;

    const datos: DatosRegistro = {
      nombre: nombre.trim(),
      email: email.trim(),
      telefono: telefono.trim(),
      password,
      rol,
    };
    const validacion = validar(datos);
    setErrores(validacion);
    setErrorGeneral(null);
    if (Object.keys(validacion).length > 0) return;

    setEnviando(true);
    try {
      await registrarse(datos);
    } catch (error) {
      const { campos, general } = interpretarError(error);
      setErrores(campos);
      setErrorGeneral(general);
      setEnviando(false);
      return;
    }

    // La cuenta ya existe: se inicia sesión con los mismos datos y la guardia SoloInvitados
    // lleva al usuario a la página de su rol.
    try {
      await iniciarSesion({ email: datos.email, password: datos.password });
    } catch {
      setErrorGeneral('Tu cuenta se creó, pero no pudimos iniciar sesión. Intenta desde la página de inicio de sesión.');
      setEnviando(false);
    }
  }

  return (
    <LayoutAutenticacion>
      <h2 className="autenticacion__titulo">Crea tu cuenta</h2>
      <p className="autenticacion__instruccion registro__instruccion">Elige cómo quieres usar la plataforma.</p>

      <form onSubmit={manejarEnvio} noValidate>
        {errorGeneral && (
          <div className="alerta alerta--error" role="alert">
            <CircleAlert size={16} aria-hidden="true" />
            <span>{errorGeneral}</span>
          </div>
        )}

        <fieldset className="selector-rol" disabled={enviando}>
          <legend className="solo-lectores">Tipo de cuenta</legend>
          {OPCIONES_ROL.map((opcion) => (
            <label
              key={opcion.valor}
              className={rol === opcion.valor ? 'opcion-rol opcion-rol--activa' : 'opcion-rol'}
            >
              <input
                type="radio"
                name="rol"
                value={opcion.valor}
                checked={rol === opcion.valor}
                onChange={() => setRol(opcion.valor)}
                className="solo-lectores"
              />
              <span className="opcion-rol__titulo">{opcion.titulo}</span>
              <span className="opcion-rol__descripcion">{opcion.descripcion}</span>
            </label>
          ))}
        </fieldset>

        <CampoTexto
          etiqueta="Nombre completo"
          icono={UserRound}
          name="nombre"
          autoComplete="name"
          placeholder="Tu nombre y apellido"
          maxLength={100}
          value={nombre}
          onChange={(e) => setNombre(e.target.value)}
          error={errores.nombre}
          disabled={enviando}
        />

        <CampoTexto
          etiqueta="Correo electrónico"
          icono={Mail}
          type="email"
          name="email"
          autoComplete="email"
          placeholder="tu@correo.com"
          maxLength={254}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={errores.email}
          disabled={enviando}
        />

        <CampoTexto
          etiqueta="Teléfono"
          type="tel"
          name="telefono"
          autoComplete="tel"
          inputMode="tel"
          placeholder="+57 300 123 4567"
          maxLength={20}
          value={telefono}
          onChange={(e) => setTelefono(e.target.value)}
          error={errores.telefono}
          disabled={enviando}
        />

        <CampoTexto
          etiqueta="Contraseña"
          icono={Lock}
          type={verPassword ? 'text' : 'password'}
          name="password"
          autoComplete="new-password"
          placeholder="••••••••"
          maxLength={72}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errores.password}
          ayuda="Mínimo 8 caracteres."
          disabled={enviando}
          accesorio={<BotonVerContrasena visible={verPassword} alCambiar={() => setVerPassword((v) => !v)} />}
        />

        <button type="submit" className="boton boton--primario boton--bloque registro__enviar" disabled={enviando}>
          {enviando && <LoaderCircle size={16} className="girando" aria-hidden="true" />}
          {enviando ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>
      </form>

      <p className="autenticacion__pie">
        ¿Ya tienes cuenta?{' '}
        <Link to={RUTAS.login} className="enlace">
          Inicia sesión
        </Link>
      </p>
    </LayoutAutenticacion>
  );
}
