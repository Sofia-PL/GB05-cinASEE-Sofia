import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';

// Importación de la página principal de Inicio
import Inicio from './paginas/Inicio';

// Importaciones desde la subcarpeta 'usuario'
import CarteleraPage from './paginas/usuario/CarteleraPage';      
import LoginPage from './paginas/usuario/LogInPage';
import PerfilPage from './paginas/usuario/PerfilPage';
import Cines from './paginas/usuario/Cines';
import CineDetalle from './paginas/usuario/CineDetalle';

// Importaciones desde la subcarpeta 'empleados'
import ValidacionQRPage from './paginas/empleados/ValidacionQRPage';
import TaquillaPage from './paginas/empleados/TaquillaVentaPage';

// Importaciones desde la subcarpeta 'admin'
import AdminPeliculasPage from './paginas/admin/AdminPeliculasPage';
import AdminSalasPage from './paginas/admin/AdminSalasPage';

export default function App() {
  return (
    <BrowserRouter>
      {/* BARRA DE NAVEGACIÓN SUPERIOR  */}
      <nav className="site-navbar">
        <div className="site-navbar-brand">
          <Link to="/" className="site-navbar-logo">CinASEE</Link>
        </div>
        <div className="site-navbar-links">
          <Link to="/cines" className="site-navbar-link">Cines</Link>
          <Link to="/cartelera" className="site-navbar-link">Cartelera</Link>
          <Link to="/entradas" className="site-navbar-link">Entradas</Link>
        </div>
        <div className="site-navbar-actions">
          <Link to="/login" className="site-navbar-link">Iniciar sesión</Link>
          <Link to="/registro" className="site-navbar-register">Registrarse</Link>
        </div>
      </nav>

      {/* Renderizado dinámico de las páginas según la URL */}
      <main className="site-main">
        <Routes>
          {/* Lo primero que sale al entrar a la web  */}
          <Route path="/" element={<Inicio />} />
          
          {/* Rutas de Usuario / Cliente */}
          <Route path="/cartelera" element={<CarteleraPage />} />
          <Route path="/entradas" element={<CarteleraPage />} />
          <Route path="/cines" element={<Cines />} />
          <Route path="/cines/:cineId" element={<CineDetalle />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/registro" element={<LoginPage />} />
          <Route path="/perfil" element={<PerfilPage />} />

          {/* Rutas de Empleados */}
          <Route path="/empleados/validar-qr" element={<ValidacionQRPage />} />
          <Route path="/empleados/taquilla" element={<TaquillaPage />} />

          {/* Rutas de Administrador */}
          <Route path="/admin/peliculas" element={<AdminPeliculasPage />} />
          <Route path="/admin/salas" element={<AdminSalasPage />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}