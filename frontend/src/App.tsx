import { Navigate, Route, Routes } from 'react-router';
import { LayoutApp } from './components/LayoutApp';
import AlojamientosAdminPage from './pages/admin/AlojamientosAdminPage';
import CatalogosPage from './pages/admin/CatalogosPage';
import DetallePublicacionPage from './pages/admin/DetallePublicacionPage';
import LayoutAdmin from './pages/admin/LayoutAdmin';
import PanelAdminPage from './pages/admin/PanelAdminPage';
import RevisionPublicacionesPage from './pages/admin/RevisionPublicacionesPage';
import DisponibilidadPage from './pages/anfitrion/disponibilidad/DisponibilidadPage';
import FormularioAlojamientoPage from './pages/anfitrion/formulario/FormularioAlojamientoPage';
import LayoutAnfitrion from './pages/anfitrion/LayoutAnfitrion';
import MisAlojamientosPage from './pages/anfitrion/MisAlojamientosPage';
import ReservasRecibidasPage from './pages/anfitrion/ReservasRecibidasPage';
import LoginPage from './pages/login/LoginPage';
import InicioProvisional from './pages/provisionales/InicioProvisional';
import PaginaPendiente from './pages/provisionales/PaginaPendiente';
import RegistroPage from './pages/registro/RegistroPage';
import { RedireccionInicial, RutaProtegida, SoloInvitados } from './routes/Guardias';
import { RUTAS } from './routes/rutas';

export default function App() {
  return (
    <Routes>
      <Route index element={<RedireccionInicial />} />

      <Route element={<SoloInvitados />}>
        <Route path={RUTAS.login} element={<LoginPage />} />
        <Route path={RUTAS.registro} element={<RegistroPage />} />
        <Route path={RUTAS.recuperarContrasena} element={<PaginaPendiente titulo="Recuperar contraseña" />} />
      </Route>

      <Route element={<RutaProtegida roles={['HUESPED']} />}>
        <Route path={RUTAS.huesped} element={<LayoutApp />}>
          <Route index element={<InicioProvisional />} />
        </Route>
      </Route>

      <Route element={<RutaProtegida roles={['ANFITRION']} />}>
        <Route path={RUTAS.anfitrion} element={<LayoutAnfitrion />}>
          <Route index element={<MisAlojamientosPage />} />
          <Route path="alojamientos/nuevo" element={<FormularioAlojamientoPage key="nuevo" />} />
          <Route path="alojamientos/:id/editar" element={<FormularioAlojamientoPage key="editar" />} />
          <Route path="disponibilidad" element={<DisponibilidadPage />} />
          <Route path="reservas" element={<ReservasRecibidasPage />} />
        </Route>
      </Route>

      <Route element={<RutaProtegida roles={['ADMINISTRADOR']} />}>
        <Route path={RUTAS.admin} element={<LayoutAdmin />}>
          <Route index element={<PanelAdminPage />} />
          <Route path="publicaciones" element={<RevisionPublicacionesPage />} />
          <Route path="publicaciones/:id" element={<DetallePublicacionPage />} />
          <Route path="alojamientos" element={<AlojamientosAdminPage />} />
          <Route path="catalogos" element={<CatalogosPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
