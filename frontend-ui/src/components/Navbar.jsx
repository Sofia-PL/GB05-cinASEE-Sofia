import { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [scrolled, setScrolled] = useState(false);

  const isHome = location.pathname === '/';

  /* Detectar scroll para cambiar el estilo del navbar */
  useEffect(() => {
    function onScroll() {
      setScrolled(window.scrollY > 20);
    }
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  function handleLogout() {
    logout();
    navigate('/');
  }

  // En la home mantiene el degradado sobre el carrusel; en el resto fondo blanco con letras granates
  const navClass = `site-navbar${!isHome ? ' site-navbar--subpage' : ''}${scrolled ? ' site-navbar--scrolled' : ''}`;

  return (
    <nav className={navClass} aria-label="Navegación principal">
      {/* Logo */}
      <div className="site-navbar-brand">
        <Link to="/" className="site-navbar-logo">CinASEE</Link>
      </div>

      {/* Links centrales — varían según el rol */}
      <div className="site-navbar-links">
        <Link to="/cines"     className="site-navbar-link">Cines</Link>
        <Link to="/cartelera" className="site-navbar-link">Cartelera</Link>

        {/* USER y sin login */}
        {(!user || user.rol === 'USER') && null}

        {/* STAFF */}
        {user?.rol === 'STAFF' && (
          <>
            <Link to="/empleados/validar-qr" className="site-navbar-link">Validar QR</Link>
            <Link to="/empleados/taquilla"   className="site-navbar-link">Taquilla</Link>
          </>
        )}

        {/* ADMIN */}
        {user?.rol === 'ADMIN' && (
          <>
            <Link to="/admin/peliculas" className="site-navbar-link">Películas</Link>
            <Link to="/admin/salas"     className="site-navbar-link">Salas</Link>
            <Link to="/admin/sesiones"  className="site-navbar-link">Sesiones</Link>
            <Link to="/admin/metricas"  className="site-navbar-link">Métricas</Link>
          </>
        )}
      </div>

      {/* Acciones (login / perfil) */}
      <div className="site-navbar-actions">
        {!user ? (
          <>
            <Link to="/login"    className="site-navbar-link">Iniciar sesión</Link>
            <Link to="/registro" className="site-navbar-register">Registrarse</Link>
          </>
        ) : (
          <>
            <Link to="/perfil" className="site-navbar-link">
              {user.name}
            </Link>
            <button
              className="site-navbar-register site-navbar-logout"
              type="button"
              onClick={handleLogout}
            >
              Cerrar sesión
            </button>
          </>
        )}
      </div>
    </nav>
  );
}

