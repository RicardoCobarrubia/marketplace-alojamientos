import { Navigate, Route, Routes } from 'react-router';
import AlojamientosAdminPage from './pages/admin/AlojamientosAdminPage';
import AuditoriaPage from './pages/admin/AuditoriaPage';
import CatalogosPage from './pages/admin/CatalogosPage';
import DetallePublicacionPage from './pages/admin/DetallePublicacionPage';
import LayoutAdmin from './pages/admin/LayoutAdmin';
import PanelAdminPage from './pages/admin/PanelAdminPage';
import ResenasAdminPage from './pages/admin/ResenasAdminPage';
import ReportesPage from './pages/admin/ReportesPage';
import ReservasRevisionPage from './pages/admin/ReservasRevisionPage';
import RevisionPublicacionesPage from './pages/admin/RevisionPublicacionesPage';
import TarifasPage from './pages/admin/TarifasPage';
import DisponibilidadPage from './pages/anfitrion/disponibilidad/DisponibilidadPage';
import FormularioAlojamientoPage from './pages/anfitrion/formulario/FormularioAlojamientoPage';
import LayoutAnfitrion from './pages/anfitrion/LayoutAnfitrion';
import MisAlojamientosPage from './pages/anfitrion/MisAlojamientosPage';
import DetalleReservaPage from './pages/anfitrion/DetalleReservaPage';
import ReservasRecibidasPage from './pages/anfitrion/ReservasRecibidasPage';
import NotificacionesPage from './pages/comunes/NotificacionesPage';
import PerfilPage from './pages/comunes/PerfilPage';
import LoginPage from './pages/login/LoginPage';
import AlojamientoPage from './pages/huesped/AlojamientoPage';
import DetalleReservaHuespedPage from './pages/huesped/DetalleReservaHuespedPage';
import ExplorarPage from './pages/huesped/ExplorarPage';
import LayoutHuesped from './pages/huesped/LayoutHuesped';
import MisReservasPage from './pages/huesped/MisReservasPage';
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
        <Route path={RUTAS.huesped} element={<LayoutHuesped />}>
          <Route index element={<ExplorarPage />} />
          <Route path="reservas" element={<MisReservasPage />} />
          <Route path="reservas/:id" element={<DetalleReservaHuespedPage />} />
          <Route path="alojamientos/:id" element={<AlojamientoPage />} />
          <Route path="notificaciones" element={<NotificacionesPage />} />
          <Route path="perfil" element={<PerfilPage />} />
        </Route>
      </Route>

      <Route element={<RutaProtegida roles={['ANFITRION']} />}>
        <Route path={RUTAS.anfitrion} element={<LayoutAnfitrion />}>
          <Route index element={<MisAlojamientosPage />} />
          <Route path="alojamientos/nuevo" element={<FormularioAlojamientoPage key="nuevo" />} />
          <Route path="alojamientos/:id/editar" element={<FormularioAlojamientoPage key="editar" />} />
          <Route path="disponibilidad" element={<DisponibilidadPage />} />
          <Route path="reservas" element={<ReservasRecibidasPage />} />
          <Route path="reservas/:id" element={<DetalleReservaPage />} />
          <Route path="notificaciones" element={<NotificacionesPage />} />
          <Route path="perfil" element={<PerfilPage />} />
        </Route>
      </Route>

      <Route element={<RutaProtegida roles={['ADMINISTRADOR']} />}>
        <Route path={RUTAS.admin} element={<LayoutAdmin />}>
          <Route index element={<PanelAdminPage />} />
          <Route path="publicaciones" element={<RevisionPublicacionesPage />} />
          <Route path="publicaciones/:id" element={<DetallePublicacionPage />} />
          <Route path="alojamientos" element={<AlojamientosAdminPage />} />
          <Route path="catalogos" element={<CatalogosPage />} />
          <Route path="reservas-en-revision" element={<ReservasRevisionPage />} />
          <Route path="resenas" element={<ResenasAdminPage />} />
          <Route path="tarifas" element={<TarifasPage />} />
          <Route path="auditoria" element={<AuditoriaPage />} />
          <Route path="reportes" element={<ReportesPage />} />
          <Route path="notificaciones" element={<NotificacionesPage />} />
          <Route path="perfil" element={<PerfilPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
