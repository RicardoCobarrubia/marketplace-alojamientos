import { Navigate, Route, Routes } from 'react-router';
import LoginPage from './pages/login/LoginPage';
import InicioProvisional from './pages/provisionales/InicioProvisional';
import PaginaPendiente from './pages/provisionales/PaginaPendiente';
import { RedireccionInicial, RutaProtegida, SoloInvitados } from './routes/Guardias';
import { RUTAS } from './routes/rutas';

export default function App() {
  return (
    <Routes>
      <Route index element={<RedireccionInicial />} />

      <Route element={<SoloInvitados />}>
        <Route path={RUTAS.login} element={<LoginPage />} />
        <Route path={RUTAS.registro} element={<PaginaPendiente titulo="Crear cuenta" />} />
        <Route path={RUTAS.recuperarContrasena} element={<PaginaPendiente titulo="Recuperar contraseña" />} />
      </Route>

      <Route element={<RutaProtegida roles={['HUESPED']} />}>
        <Route path={RUTAS.huesped} element={<InicioProvisional />} />
      </Route>

      <Route element={<RutaProtegida roles={['ANFITRION']} />}>
        <Route path={RUTAS.anfitrion} element={<InicioProvisional />} />
      </Route>

      <Route element={<RutaProtegida roles={['ADMINISTRADOR']} />}>
        <Route path={RUTAS.admin} element={<InicioProvisional />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
