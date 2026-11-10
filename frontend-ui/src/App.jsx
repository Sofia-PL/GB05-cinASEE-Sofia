import { BrowserRouter, Routes, Route } from 'react-router-dom';

import { AuthProvider } from './context/AuthContext';
import { CineProvider } from './context/CineContext';
import Navbar from './components/Navbar';
import Footer from './components/Footer';

// Importación de la página principal de Inicio
import Inicio from './paginas/Inicio';

// Importaciones desde la subcarpeta 'usuario'
import CarteleraPage from './paginas/usuario/CarteleraPage';
import LoginPage from './paginas/usuario/LogInPage';
import PerfilPage from './paginas/usuario/PerfilPage';
import Cines from './paginas/usuario/Cines';
import CineDetalle from './paginas/usuario/CineDetalle';
import CompraPage from './paginas/usuario/CompraPage';

// Importaciones desde la subcarpeta 'empleados'
import ValidacionQRPage from './paginas/empleados/ValidacionQRPage';
import TaquillaPage from './paginas/empleados/TaquillaVentaPage';

// Importaciones desde la subcarpeta 'admin'
import AdminPeliculasPage from './paginas/admin/AdminPeliculasPage';
import AdminSalasPage from './paginas/admin/AdminSalasPage';
import AdminSesionesPage from './paginas/admin/AdminSesionesPage';
import AdminMetricasPage from './paginas/admin/AdminMetricasPage';

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
              <Route path="/entradas" element={<CarteleraPage />} />
              <Route path="/cines" element={<Cines />} />
              <Route path="/cines/:cineId" element={<CineDetalle />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/registro" element={<LoginPage />} />
              <Route path="/perfil" element={<PerfilPage />} />
              <Route path="/compra" element={<CompraPage />} />

              {/* Rutas de Empleados */}
              <Route path="/empleados/validar-qr" element={<ValidacionQRPage />} />
              <Route path="/empleados/taquilla" element={<TaquillaPage />} />

              {/* Rutas de Administrador */}
              <Route path="/admin/peliculas" element={<AdminPeliculasPage />} />
              <Route path="/admin/salas" element={<AdminSalasPage />} />
              <Route path="/admin/sesiones" element={<AdminSesionesPage />} />
              <Route path="/admin/metricas" element={<AdminMetricasPage />} />
            </Routes>
          </main>
          {/* Pie de página con información de contacto, datos legales y enlaces */}
          <Footer />
        </BrowserRouter>
      </CineProvider>
    </AuthProvider>
  );
}
