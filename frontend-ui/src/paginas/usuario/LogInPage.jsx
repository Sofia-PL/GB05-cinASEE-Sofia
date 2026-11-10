import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, register } = useAuth();

  const defaultTab = location.pathname === '/registro' ? 'registro' : 'login';
  const [tab, setTab] = useState(defaultTab);
  const [error, setError] = useState('');

  const [loginData, setLoginData] = useState({ email: '', password: '' });
  const [regData, setRegData] = useState({ name: '', email: '', password: '', confirm: '', rol: 'USER' });

  function redirigirSegunRol(rol) {
    if (rol === 'ADMIN') {
      navigate('/admin/peliculas');
    } else if (rol === 'STAFF') {
      navigate('/empleados/validar-qr');
    } else {
      navigate('/');
    }
  }

  function handleLogin(e) {
    e.preventDefault();
    setError('');
    const result = login(loginData.email, loginData.password);
    if (result.ok) {
      redirigirSegunRol(result.user?.rol);
    } else {
      setError(result.error);
    }
  }

  function loginDemo(email, pass) {
    setError('');
    const result = login(email, pass);
    if (result.ok) {
      redirigirSegunRol(result.user?.rol);
    } else {
      setError(result.error);
    }
  }

  function handleRegister(e) {
    e.preventDefault();
    setError('');
    if (!regData.name.trim() || !regData.email.trim() || !regData.password) {
      setError('Por favor, completa todos los campos.');
      return;
    }
    if (regData.password !== regData.confirm) {
      setError('Las contraseñas no coinciden');
      return;
    }
    if (regData.password.length < 4) {
      setError('La contraseña debe tener al menos 4 caracteres');
      return;
    }
    const result = register(regData.name, regData.email, regData.password, regData.rol);
    if (result.ok) {
      redirigirSegunRol(result.user?.rol);
    } else {
      setError(result.error);
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <h1 className="login-title">CinASEE</h1>

        {/* Acceso rápido para probar perfiles sin teclear */}
        <div className="login-quick-access">
          <p className="login-quick-title">Acceso rápido por perfil (modo de prueba):</p>
          <p style={{ margin: '0 0 10px', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
            Pulsa en cualquiera de los tres perfiles para probar directamente sus pestañas y vistas correspondientes:
          </p>
          <div className="login-roles-grid">
            <button
              type="button"
              className="login-role-btn login-role-btn--admin"
              onClick={() => loginDemo('admin@cinasee.es', 'admin123')}
            >
              <div>
                <strong>Administrador (Prueba)</strong>
                <small>Acceso a Películas, Salas, Sesiones y Métricas</small>
              </div>
            </button>
            <button
              type="button"
              className="login-role-btn login-role-btn--staff"
              onClick={() => loginDemo('staff@cinasee.es', 'staff123')}
            >
              <div>
                <strong>Personal / Staff (Prueba)</strong>
                <small>Acceso a Validación QR y Terminal de Taquilla</small>
              </div>
            </button>
            <button
              type="button"
              className="login-role-btn login-role-btn--user"
              onClick={() => loginDemo('usuario@cinasee.es', 'user123')}
            >
              <div>
                <strong>Cliente / Usuario (Prueba)</strong>
                <small>Acceso a Cartelera, Compra y Perfil</small>
              </div>
            </button>
          </div>
        </div>

        <div className="login-divider">
          <span>O introduce tus credenciales manualmente</span>
        </div>

        {/* Pestañas Iniciar sesión / Registro */}
        <div className="login-tabs" role="tablist">
          <button
            role="tab"
            aria-selected={tab === 'login'}
            className={`login-tab${tab === 'login' ? ' active' : ''}`}
            onClick={() => { setTab('login'); setError(''); }}
          >
            Iniciar sesión
          </button>
          <button
            role="tab"
            aria-selected={tab === 'registro'}
            className={`login-tab${tab === 'registro' ? ' active' : ''}`}
            onClick={() => { setTab('registro'); setError(''); }}
          >
            Registrarse
          </button>
        </div>

        {/* Caja de información con los usuarios guardados para probar */}
        {tab === 'login' && (
          <div style={{
            background: 'rgba(217, 164, 65, 0.12)',
            border: '1px solid rgba(217, 164, 65, 0.35)',
            borderRadius: '10px',
            padding: '12px 16px',
            marginBottom: '18px',
            fontSize: '0.85rem',
            color: 'var(--dark-burgundy)'
          }}>
            <strong style={{ display: 'block', marginBottom: '6px', color: 'var(--granate)' }}>
              Usuarios guardados en el sistema para iniciar sesión:
            </strong>
            <ul style={{ margin: 0, paddingLeft: '18px', lineHeight: 1.5 }}>
              <li><strong>Admin:</strong> admin@cinasee.es (contraseña: admin123)</li>
              <li><strong>Personal:</strong> staff@cinasee.es (contraseña: staff123)</li>
              <li><strong>Usuario:</strong> usuario@cinasee.es (contraseña: user123)</li>
            </ul>
          </div>
        )}

        {error && <p className="login-error" role="alert">{error}</p>}

        {tab === 'login' && (
          <form className="login-form" onSubmit={handleLogin} noValidate>
            <div className="login-field">
              <label htmlFor="login-email">Correo electrónico</label>
              <input
                id="login-email"
                type="email"
                autoComplete="email"
                placeholder="ejemplo@cinasee.com"
                required
                value={loginData.email}
                onChange={(e) => setLoginData((p) => ({ ...p, email: e.target.value }))}
              />
            </div>
            <div className="login-field">
              <label htmlFor="login-pass">Contraseña</label>
              <input
                id="login-pass"
                type="password"
                autoComplete="current-password"
                placeholder="Introduce tu contraseña"
                required
                value={loginData.password}
                onChange={(e) => setLoginData((p) => ({ ...p, password: e.target.value }))}
              />
            </div>
            <button className="login-submit home-button" type="submit">
              Acceder a la cuenta
            </button>
          </form>
        )}

        {tab === 'registro' && (
          <form className="login-form" onSubmit={handleRegister} noValidate>
            <div className="login-field">
              <label htmlFor="reg-name">Nombre y apellidos</label>
              <input
                id="reg-name"
                type="text"
                autoComplete="name"
                placeholder="Tu nombre completo"
                required
                value={regData.name}
                onChange={(e) => setRegData((p) => ({ ...p, name: e.target.value }))}
              />
            </div>
            <div className="login-field">
              <label htmlFor="reg-email">Correo electrónico</label>
              <input
                id="reg-email"
                type="email"
                autoComplete="email"
                placeholder="tu@email.com"
                required
                value={regData.email}
                onChange={(e) => setRegData((p) => ({ ...p, email: e.target.value }))}
              />
            </div>
            <div className="login-field">
              <label htmlFor="reg-rol">Tipo de perfil</label>
              <select
                id="reg-rol"
                value={regData.rol}
                onChange={(e) => setRegData((p) => ({ ...p, rol: e.target.value }))}
                style={{
                  padding: '10px 14px',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid rgba(43, 18, 21, 0.2)',
                  font: 'inherit',
                  background: 'white',
                  color: 'var(--dark-burgundy)'
                }}
              >
                <option value="USER">Cliente / Espectador</option>
                <option value="STAFF">Personal de sala y taquilla</option>
                <option value="ADMIN">Administrador de cine</option>
              </select>
            </div>
            <div className="login-field">
              <label htmlFor="reg-pass">Contraseña</label>
              <input
                id="reg-pass"
                type="password"
                autoComplete="new-password"
                placeholder="Mínimo 4 caracteres"
                required
                value={regData.password}
                onChange={(e) => setRegData((p) => ({ ...p, password: e.target.value }))}
              />
            </div>
            <div className="login-field">
              <label htmlFor="reg-confirm">Confirmar contraseña</label>
              <input
                id="reg-confirm"
                type="password"
                autoComplete="new-password"
                placeholder="Repite la contraseña"
                required
                value={regData.confirm}
                onChange={(e) => setRegData((p) => ({ ...p, confirm: e.target.value }))}
              />
            </div>
            <button className="login-submit home-button" type="submit">
              Crear cuenta
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
