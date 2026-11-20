import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function EyeIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

function EyeSlashIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
      <line x1="1" y1="1" x2="23" y2="23" />
    </svg>
  );
}

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, register, sesionCaducada } = useAuth();

  const defaultTab = location.pathname === '/registro' ? 'registro' : 'login';
  const [tab, setTab] = useState(defaultTab);
  const [errorGlobal, setErrorGlobal] = useState('');
  const [cargando, setCargando] = useState(false);
  const [campoErrores, setCampoErrores] = useState({});

  const [loginData, setLoginData] = useState({ email: '', password: '' });
  const [regData, setRegData] = useState({ name: '', email: '', password: '', confirm: '' });

  const [mostrarLoginPass, setMostrarLoginPass] = useState(false);
  const [mostrarRegPass, setMostrarRegPass] = useState(false);
  const [mostrarRegConfirm, setMostrarRegConfirm] = useState(false);

  function redirigirSegunRol(rol) {
    // Si se llegó al login desde la compra, se vuelve a ella para terminarla
    const volverA = location.state?.from;
    if (volverA) {
      navigate(volverA, { replace: true });
      return;
    }
    if (rol === 'ADMIN') {
      navigate('/admin/peliculas');
    } else if (rol === 'STAFF') {
      navigate('/empleados/validar-qr');
    } else {
      navigate('/');
    }
  }

  function limpiarErrores() {
    setErrorGlobal('');
    setCampoErrores({});
  }

  async function handleLogin(e) {
    e.preventDefault();
    limpiarErrores();

    const nuevosErrores = {};
    const emailTrim = (loginData.email || '').trim();
    const passTrim = (loginData.password || '').trim();

    if (!emailTrim) {
      nuevosErrores.email = 'Introduce tu correo electrónico.';
    } else if (!EMAIL_REGEX.test(emailTrim)) {
      nuevosErrores.email = 'El formato del correo electrónico no es válido.';
    }

    if (!passTrim) {
      nuevosErrores.password = 'Introduce tu contraseña.';
    }

    if (Object.keys(nuevosErrores).length > 0) {
      setCampoErrores(nuevosErrores);
      setErrorGlobal('Por favor, revisa los campos marcados en rojo.');
      return;
    }

    setCargando(true);
    try {
      const result = await login(emailTrim, passTrim);
      if (result.ok) {
        redirigirSegunRol(result.user?.rol);
      } else {
        setErrorGlobal(result.error || 'Credenciales inválidas.');
      }
    } catch {
      setErrorGlobal('Error inesperado al iniciar sesión.');
    } finally {
      setCargando(false);
    }
  }

  async function handleRegister(e) {
    e.preventDefault();
    limpiarErrores();

    const nuevosErrores = {};
    const nameTrim = (regData.name || '').trim();
    const emailTrim = (regData.email || '').trim();
    const pass = regData.password || '';
    const confirm = regData.confirm || '';

    if (!nameTrim) {
      nuevosErrores.name = 'Introduce tu nombre y apellidos.';
    } else if (nameTrim.length < 3) {
      nuevosErrores.name = 'El nombre debe tener al menos 3 caracteres.';
    }

    if (!emailTrim) {
      nuevosErrores.email = 'Introduce tu correo electrónico.';
    } else if (!EMAIL_REGEX.test(emailTrim)) {
      nuevosErrores.email = 'El correo electrónico no tiene un formato válido.';
    }

    if (!pass) {
      nuevosErrores.password = 'Introduce una contraseña.';
    } else if (pass.length < 8) {
      nuevosErrores.password = 'La contraseña debe tener un mínimo de 8 caracteres (requisito del sistema).';
    }

    if (!confirm) {
      nuevosErrores.confirm = 'Confirma tu contraseña.';
    } else if (pass !== confirm) {
      nuevosErrores.confirm = 'Las contraseñas no coinciden.';
    }

    if (Object.keys(nuevosErrores).length > 0) {
      setCampoErrores(nuevosErrores);
      setErrorGlobal('Corrige los errores antes de continuar.');
      return;
    }

    setCargando(true);
    try {
      const result = await register(nameTrim, emailTrim, pass);
      if (result.ok) {
        redirigirSegunRol('USER');
      } else {
        setErrorGlobal(result.error || 'Error al crear la cuenta.');
      }
    } catch {
      setErrorGlobal('Error inesperado al procesar el registro.');
    } finally {
      setCargando(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <h1 className="login-title">CinASEE</h1>

        {/* Pestañas Iniciar sesión / Registro */}
        <div className="login-tabs" role="tablist">
          <button
            role="tab"
            aria-selected={tab === 'login'}
            className={`login-tab${tab === 'login' ? ' active' : ''}`}
            onClick={() => { setTab('login'); limpiarErrores(); }}
          >
            Iniciar sesión
          </button>
          <button
            role="tab"
            aria-selected={tab === 'registro'}
            className={`login-tab${tab === 'registro' ? ' active' : ''}`}
            onClick={() => { setTab('registro'); limpiarErrores(); }}
          >
            Registrarse
          </button>
        </div>

        {sesionCaducada && !errorGlobal && tab === 'login' && (
          <div className="cinasee-aviso" role="status" style={{ marginBottom: '16px' }}>
            Tu sesión ha caducado. Vuelve a iniciar sesión para continuar.
          </div>
        )}

        {errorGlobal && (
          <div
            className="login-error"
            role="alert"
            style={{
              padding: '10px 14px',
              borderRadius: '6px',
              background: '#fde8e8',
              border: '1px solid #f8b4b4',
              color: '#9b1c1c',
              marginBottom: '16px',
              fontSize: '0.88rem',
              fontWeight: 600
            }}
          >
            {errorGlobal}
          </div>
        )}

        {tab === 'login' && (
          <form className="login-form" onSubmit={handleLogin} noValidate>
            <div className="login-field">
              <label htmlFor="login-email">Correo electrónico</label>
              <input
                id="login-email"
                type="email"
                autoComplete="email"
                placeholder="ejemplo@cinasee.com"
                value={loginData.email}
                onChange={(e) => {
                  setLoginData((p) => ({ ...p, email: e.target.value }));
                  if (campoErrores.email) setCampoErrores((prev) => ({ ...prev, email: '' }));
                }}
                style={campoErrores.email ? { borderColor: '#d9383a', background: '#fff5f5' } : {}}
              />
              {campoErrores.email && (
                <span style={{ color: '#d9383a', fontSize: '0.8rem', marginTop: '4px' }}>
                  {campoErrores.email}
                </span>
              )}
            </div>

            <div className="login-field">
              <label htmlFor="login-pass">Contraseña</label>
              <div className="login-password-wrapper">
                <input
                  id="login-pass"
                  type={mostrarLoginPass ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Introduce tu contraseña"
                  value={loginData.password}
                  onChange={(e) => {
                    setLoginData((p) => ({ ...p, password: e.target.value }));
                    if (campoErrores.password) setCampoErrores((prev) => ({ ...prev, password: '' }));
                  }}
                  style={campoErrores.password ? { borderColor: '#d9383a', background: '#fff5f5' } : {}}
                />
                <button
                  type="button"
                  className="login-toggle-password"
                  onClick={() => setMostrarLoginPass((prev) => !prev)}
                  title={mostrarLoginPass ? 'Ocultar contraseña' : 'Ver contraseña'}
                  aria-label={mostrarLoginPass ? 'Ocultar contraseña' : 'Ver contraseña'}
                >
                  {mostrarLoginPass ? <EyeSlashIcon /> : <EyeIcon />}
                </button>
              </div>
              {campoErrores.password && (
                <span style={{ color: '#d9383a', fontSize: '0.8rem', marginTop: '4px' }}>
                  {campoErrores.password}
                </span>
              )}
            </div>

            <button
              className="login-submit home-button"
              type="submit"
              disabled={cargando}
              style={{ opacity: cargando ? 0.7 : 1, cursor: cargando ? 'not-allowed' : 'pointer' }}
            >
              {cargando ? 'Accediendo...' : 'Acceder a la cuenta'}
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
                value={regData.name}
                onChange={(e) => {
                  setRegData((p) => ({ ...p, name: e.target.value }));
                  if (campoErrores.name) setCampoErrores((prev) => ({ ...prev, name: '' }));
                }}
                style={campoErrores.name ? { borderColor: '#d9383a', background: '#fff5f5' } : {}}
              />
              {campoErrores.name && (
                <span style={{ color: '#d9383a', fontSize: '0.8rem', marginTop: '4px' }}>
                  {campoErrores.name}
                </span>
              )}
            </div>

            <div className="login-field">
              <label htmlFor="reg-email">Correo electrónico</label>
              <input
                id="reg-email"
                type="email"
                autoComplete="email"
                placeholder="tu@email.com"
                value={regData.email}
                onChange={(e) => {
                  setRegData((p) => ({ ...p, email: e.target.value }));
                  if (campoErrores.email) setCampoErrores((prev) => ({ ...prev, email: '' }));
                }}
                style={campoErrores.email ? { borderColor: '#d9383a', background: '#fff5f5' } : {}}
              />
              {campoErrores.email && (
                <span style={{ color: '#d9383a', fontSize: '0.8rem', marginTop: '4px' }}>
                  {campoErrores.email}
                </span>
              )}
            </div>

            <div className="login-field">
              <label htmlFor="reg-pass">Contraseña (mínimo 8 caracteres)</label>
              <div className="login-password-wrapper">
                <input
                  id="reg-pass"
                  type={mostrarRegPass ? 'text' : 'password'}
                  autoComplete="new-password"
                  placeholder="Mínimo 8 caracteres"
                  value={regData.password}
                  onChange={(e) => {
                    setRegData((p) => ({ ...p, password: e.target.value }));
                    if (campoErrores.password) setCampoErrores((prev) => ({ ...prev, password: '' }));
                  }}
                  style={campoErrores.password ? { borderColor: '#d9383a', background: '#fff5f5' } : {}}
                />
                <button
                  type="button"
                  className="login-toggle-password"
                  onClick={() => setMostrarRegPass((prev) => !prev)}
                  title={mostrarRegPass ? 'Ocultar contraseña' : 'Ver contraseña'}
                  aria-label={mostrarRegPass ? 'Ocultar contraseña' : 'Ver contraseña'}
                >
                  {mostrarRegPass ? <EyeSlashIcon /> : <EyeIcon />}
                </button>
              </div>
              {campoErrores.password && (
                <span style={{ color: '#d9383a', fontSize: '0.8rem', marginTop: '4px' }}>
                  {campoErrores.password}
                </span>
              )}
            </div>

            <div className="login-field">
              <label htmlFor="reg-confirm">Confirmar contraseña</label>
              <div className="login-password-wrapper">
                <input
                  id="reg-confirm"
                  type={mostrarRegConfirm ? 'text' : 'password'}
                  autoComplete="new-password"
                  placeholder="Repite la contraseña"
                  value={regData.confirm}
                  onChange={(e) => {
                    setRegData((p) => ({ ...p, confirm: e.target.value }));
                    if (campoErrores.confirm) setCampoErrores((prev) => ({ ...prev, confirm: '' }));
                  }}
                  style={campoErrores.confirm ? { borderColor: '#d9383a', background: '#fff5f5' } : {}}
                />
                <button
                  type="button"
                  className="login-toggle-password"
                  onClick={() => setMostrarRegConfirm((prev) => !prev)}
                  title={mostrarRegConfirm ? 'Ocultar contraseña' : 'Ver contraseña'}
                  aria-label={mostrarRegConfirm ? 'Ocultar contraseña' : 'Ver contraseña'}
                >
                  {mostrarRegConfirm ? <EyeSlashIcon /> : <EyeIcon />}
                </button>
              </div>
              {campoErrores.confirm && (
                <span style={{ color: '#d9383a', fontSize: '0.8rem', marginTop: '4px' }}>
                  {campoErrores.confirm}
                </span>
              )}
            </div>

            <button
              className="login-submit home-button"
              type="submit"
              disabled={cargando}
              style={{ opacity: cargando ? 0.7 : 1, cursor: cargando ? 'not-allowed' : 'pointer' }}
            >
              {cargando ? 'Creando cuenta...' : 'Crear cuenta'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
