import { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/useAuth';

const ETIQUETA_ROL = {
  USER: 'Cliente',
  STAFF: 'Taquilla',
  ADMIN: 'Admin',
};

/**
 * Enlace del menú que se marca cuando se está en su sección: en su ruta, en sus
 * subpáginas (/cines/123 marca "Cines") y en las rutas de `tambien` (la ficha de una
 * película, /peliculas/..., marca "Cartelera").
 */
function EnlaceMenu({ to, tambien = [], className = 'site-navbar-link', children, ...resto }) {
  const { pathname } = useLocation();
  const enSeccion = [to, ...tambien].some((ruta) => pathname === ruta || pathname.startsWith(`${ruta}/`));
  return (
    <Link
      to={to}
      className={`${className}${enSeccion ? ' site-navbar-link--activa' : ''}`}
      aria-current={enSeccion ? 'page' : undefined}
      {...resto}
    >
      {children}
    </Link>
  );
}

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
        <EnlaceMenu to="/cines">Cines</EnlaceMenu>
        <EnlaceMenu to="/cartelera" tambien={['/peliculas']}>Cartelera</EnlaceMenu>

        {/* USER y sin login */}
        {(!user || user.rol === 'USER') && null}

        {/* STAFF */}
        {user?.rol === 'STAFF' && (
          <>
            <EnlaceMenu to="/empleados/validar-qr">Validar entradas</EnlaceMenu>
            <EnlaceMenu to="/empleados/taquilla">Taquilla</EnlaceMenu>
            {user?.canReviewRoleRequests && (
              <EnlaceMenu to="/admin/solicitudes">Solicitudes</EnlaceMenu>
            )}
          </>
        )}

        {/* ADMIN */}
        {user?.rol === 'ADMIN' && (
          <>
            <EnlaceMenu to="/admin/peliculas">Películas</EnlaceMenu>
            <EnlaceMenu to="/admin/salas">Salas</EnlaceMenu>
            <EnlaceMenu to="/admin/sesiones">Sesiones</EnlaceMenu>
            <EnlaceMenu to="/admin/metricas">Métricas</EnlaceMenu>
            <EnlaceMenu to="/admin/solicitudes">Solicitudes</EnlaceMenu>
          </>
        )}
      </div>

      {/* Acciones (login / perfil) */}
      <div className="site-navbar-actions">
        {!user ? (
          <>
            <EnlaceMenu to="/login">Iniciar sesión</EnlaceMenu>
            <Link to="/registro" className="site-navbar-register">Registrarse</Link>
          </>
        ) : (
          <>
            <EnlaceMenu to="/perfil" className="site-navbar-link site-navbar-cuenta" title={user.email}>
              {user.rol === 'USER' ? (
                'Mi cuenta'
              ) : (
                <span className="site-navbar-rol">{ETIQUETA_ROL[user.rol] || 'Admin'}</span>
              )}
            </EnlaceMenu>
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

