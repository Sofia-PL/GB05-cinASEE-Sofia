import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/useAuth';

/**
 * Protege una página según el rol del usuario conectado.
 * - Sin sesión: lleva al login y, al entrar, vuelve a la página pedida.
 * - Con un rol que no está permitido: lleva al inicio.
 * Es solo una ayuda de navegación: la seguridad real la aplican el gateway
 * y cada microservicio comprobando el token JWT.
 *
 * @param {string[]} roles roles del frontend permitidos (USER, STAFF, ADMIN); vacío = cualquiera con sesión
 */
export default function RutaProtegida({ roles = [], children }) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (roles.length > 0 && !roles.includes(user.rol)) {
    return <Navigate to="/" replace />;
  }
  return children;
}
