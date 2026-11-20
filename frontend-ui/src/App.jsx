import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';

import { AuthProvider } from './context/AuthContext';
import { CineProvider } from './context/CineContext';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import RutaProtegida from './components/RutaProtegida';

// Importación de la página principal de Inicio
import Inicio from './paginas/Inicio';

// Importaciones desde la subcarpeta 'usuario'
import CarteleraPage from './paginas/usuario/CarteleraPage';
import LoginPage from './paginas/usuario/LogInPage';
import PerfilPage from './paginas/usuario/PerfilPage';
import Cines from './paginas/usuario/Cines';
import CineDetalle from './paginas/usuario/CineDetalle';
import PeliculaDetalle from './paginas/usuario/PeliculaDetalle';
import SolicitarRolPage from './paginas/usuario/SolicitarRolPage';
import CompraPage from './paginas/usuario/CompraPage';
import PagoPage from './paginas/usuario/PagoPage';

// Importaciones desde la subcarpeta 'empleados'
import ValidacionQRPage from './paginas/empleados/ValidacionQRPage';
import TaquillaPage from './paginas/empleados/TaquillaVentaPage';

// Importaciones desde la subcarpeta 'admin'
import AdminPeliculasPage from './paginas/admin/AdminPeliculasPage';
import AdminSalasPage from './paginas/admin/AdminSalasPage';
import AdminSesionesPage from './paginas/admin/AdminSesionesPage';
import AdminMetricasPage from './paginas/admin/AdminMetricasPage';
import AdminSolicitudesPage from './paginas/admin/AdminSolicitudesPage';

export default function App() {
  return (
    <AuthProvider>
      <CineProvider>
        <BrowserRouter>
          {/* BARRA DE NAVEGACIÓN SUPERIOR (componente separado con scroll-aware) */}
          <Navbar />

          {/* Renderizado dinámico de las páginas según la URL */}
          <main className="site-main">
            <Routes>
              {/* Página de inicio */}
              <Route path="/" element={<Inicio />} />

              {/* Rutas de Usuario / Cliente */}
              <Route path="/cartelera" element={<CarteleraPage />} />
              <Route path="/peliculas/:peliculaId" element={<PeliculaDetalle />} />
              {/* Antes era un duplicado de la cartelera: se redirige para no romper enlaces antiguos */}
              <Route path="/entradas" element={<Navigate to="/cartelera" replace />} />
              <Route path="/cines" element={<Cines />} />
              <Route path="/cines/:cineId" element={<CineDetalle />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/registro" element={<LoginPage />} />
              <Route path="/perfil" element={<RutaProtegida><PerfilPage /></RutaProtegida>} />
              <Route path="/perfil/solicitar-rol" element={<RutaProtegida roles={['USER', 'STAFF']}><SolicitarRolPage /></RutaProtegida>} />
              <Route path="/compra" element={<CompraPage />} />
              {/* Vuelta desde la web de pago de Redsys (Comp. 2 · Reservas) */}
              <Route path="/pago/redsys/vuelta" element={<PagoPage />} />

              {/* Rutas de Empleados */}
              <Route path="/empleados/validar-qr" element={<RutaProtegida roles={['STAFF', 'ADMIN']}><ValidacionQRPage /></RutaProtegida>} />
              <Route path="/empleados/taquilla" element={<RutaProtegida roles={['STAFF', 'ADMIN']}><TaquillaPage /></RutaProtegida>} />
              {/* Alias directos para empleados/admin */}
              <Route path="/taquilla" element={<Navigate to="/empleados/taquilla" replace />} />
              <Route path="/validar-qr" element={<Navigate to="/empleados/validar-qr" replace />} />

              {/* Rutas de Administrador */}
              <Route path="/admin/peliculas" element={<RutaProtegida roles={['ADMIN']}><AdminPeliculasPage /></RutaProtegida>} />
              <Route path="/admin/salas" element={<RutaProtegida roles={['ADMIN']}><AdminSalasPage /></RutaProtegida>} />
              <Route path="/admin/sesiones" element={<RutaProtegida roles={['ADMIN']}><AdminSesionesPage /></RutaProtegida>} />
              <Route path="/admin/metricas" element={<RutaProtegida roles={['ADMIN']}><AdminMetricasPage /></RutaProtegida>} />
              <Route path="/admin/solicitudes" element={<RutaProtegida roles={['ADMIN', 'STAFF']}><AdminSolicitudesPage /></RutaProtegida>} />
            </Routes>
          </main>
          {/* Pie de página con información de contacto, datos legales y enlaces */}
          <Footer />
        </BrowserRouter>
      </CineProvider>
    </AuthProvider>
  );
}
