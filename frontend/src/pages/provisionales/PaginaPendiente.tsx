import { Link } from 'react-router';
import { Logo } from '../../components/Logo';
import { RUTAS } from '../../routes/rutas';
import './Provisionales.css';

/** Marcador para pantallas que todavía no están construidas (registro, recuperar contraseña). */
export default function PaginaPendiente({ titulo }: { titulo: string }) {
  return (
    <main className="pendiente">
      <div className="pendiente__tarjeta">
        <Logo />
        <h1>{titulo}</h1>
        <p>Esta pantalla se construye en una historia posterior.</p>
        <Link to={RUTAS.login} className="enlace">
          Volver al inicio de sesión
        </Link>
      </div>
    </main>
  );
}
