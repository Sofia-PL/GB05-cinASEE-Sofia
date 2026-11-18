import { useState } from 'react';
import { ApiError, leerJwt } from '../api/http';
import * as usuariosApi from '../api/usuarios';
import { AuthContext } from './useAuth';

/* =========================================================
   Usuarios ficticios guardados para la demo
   ---------------------------------------------------------
   Los botones de "Acceso rápido" del login usan estos usuarios
   sin pasar por el backend (modo de prueba, sin token JWT).
   Cualquier otro correo se valida contra el Componente 3.
   ========================================================= */
const USUARIOS_FICTICIOS = [
  {
    email: 'admin@cinasee.es',
    passwords: ['admin123', 'admin'],
    name: 'Administrador CinASEE',
    rol: 'ADMIN',
  },
  {
    email: 'admin@cinasee.com',
    passwords: ['admin123', 'admin'],
    name: 'Administrador CinASEE',
    rol: 'ADMIN',
  },
  {
    email: 'staff@cinasee.es',
    passwords: ['staff123', 'staff'],
    name: 'Personal de Cine',
    rol: 'STAFF',
  },
  {
    email: 'staff@cinasee.com',
    passwords: ['staff123', 'staff'],
    name: 'Personal de Cine',
    rol: 'STAFF',
  },
  {
    email: 'usuario@cinasee.es',
    passwords: ['user123', 'usuario123', 'user', 'usuario'],
    name: 'Usuario CinASEE',
    rol: 'USER',
  },
  {
    email: 'user@cinasee.es',
    passwords: ['user123', 'usuario123', 'user', 'usuario'],
    name: 'Usuario CinASEE',
    rol: 'USER',
  },
  {
    email: 'user@cinasee.com',
    passwords: ['user123', 'usuario123', 'user', 'usuario'],
    name: 'Usuario CinASEE',
    rol: 'USER',
  },
];

const LS_KEY = 'cinasee_user';
const LS_REG_KEY = 'cinasee_registered_users';
const { tokenVigente, tokenInvitadoGuardado, obtenerTokenInvitado, borrarTokenInvitado } = usuariosApi;

/* Roles del Componente 3 -> roles que usan las vistas del frontend */
const ROLES_FRONT = {
  CLIENTE_REGISTRADO: 'USER',
  STAFF_TAQUILLA: 'STAFF',
  ADMIN: 'ADMIN',
  VISITANTE: 'USER',
};

function cargarUsuario() {
  try {
    const raw = localStorage.getItem(LS_KEY);
    const u = raw ? JSON.parse(raw) : null;
    // Un usuario real cuyo token ha caducado tiene que volver a iniciar sesión
    if (u && u.token && !tokenVigente(u.token, u.tokenExp)) {
      localStorage.removeItem(LS_KEY);
      return null;
    }
    return u;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(cargarUsuario);

  function guardar(userData) {
    localStorage.setItem(LS_KEY, JSON.stringify(userData));
    setUser(userData);
    return userData;
  }

  /** Construye el usuario de sesión a partir del token del Comp. 3 */
  async function sesionDesdeToken(tokenResponse, emailFallback, nameFallback) {
    const token = tokenResponse.accessToken;
    const claims = leerJwt(token) || {};
    const backendRole = tokenResponse.role || claims.role;
    const id = Number(claims.sub);

    // El nombre completo está en el perfil; si no se puede leer se usa el correo
    let name = nameFallback || emailFallback;
    try {
      const perfil = await usuariosApi.obtenerPerfil(id, token);
      if (perfil?.fullName) name = perfil.fullName;
    } catch {
      // perfil no disponible: no impide iniciar sesión
    }

    return guardar({
      id,
      name,
      email: claims.email || emailFallback,
      rol: ROLES_FRONT[backendRole] || 'USER',
      backendRole,
      token,
      tokenExp: claims.exp,
      demo: false,
    });
  }

  /**
   * Inicio de sesión.
   * - Usuarios de acceso rápido: modo de prueba local (sin backend).
   * - Resto: Componente 3 (POST /v1/iam/auth/login).
   * - Si el backend no responde: usuarios registrados en este navegador (modo prueba).
   */
  async function login(email, password) {
    const normEmail = (email || '').trim().toLowerCase();
    const normPass = (password || '').trim();

    if (!normEmail || !normPass) {
      return { ok: false, error: 'Por favor, introduce tu correo electrónico y contraseña.' };
    }

    // 1. Usuarios fijos de prueba
    const fijo = USUARIOS_FICTICIOS.find((u) => u.email.toLowerCase() === normEmail);
    if (fijo) {
      if (!fijo.passwords.includes(normPass)) {
        return { ok: false, error: 'Correo electrónico o contraseña incorrectos.' };
      }
      const userData = guardar({ name: fijo.name, email: fijo.email, rol: fijo.rol, demo: true });
      return { ok: true, user: userData };
    }

    // 2. Componente 3
    try {
      const tokenResponse = await usuariosApi.login(normEmail, normPass);
      const userData = await sesionDesdeToken(tokenResponse, normEmail);
      return { ok: true, user: userData };
    } catch (err) {
      if (!(err instanceof ApiError) || !err.network) {
        const credenciales = err.status === 400 || err.status === 401 || err.status === 404;
        return {
          ok: false,
          error: credenciales ? 'Correo electrónico o contraseña incorrectos.' : err.message,
        };
      }
    }

    // 3. Sin conexión: usuarios registrados localmente en modo prueba
    try {
      const regUsers = JSON.parse(localStorage.getItem(LS_REG_KEY) || '[]');
      const found = regUsers.find(
        (u) => u.email.toLowerCase() === normEmail && u.password === normPass
      );
      if (found) {
        const userData = guardar({ name: found.name, email: found.email, rol: found.rol, demo: true });
        return { ok: true, user: userData };
      }
    } catch {
      // ignorar
    }

    return {
      ok: false,
      error: 'No se puede conectar con el servidor de usuarios. Usa el acceso rápido (modo de prueba) o arranca el backend.',
    };
  }

  /**
   * Registro en el Componente 3 (y login automático después).
   * Si el backend no responde se registra localmente en modo de prueba.
   */
  async function register(name, email, password, rol = 'USER') {
    const cleanName = (name || '').trim();
    const cleanEmail = (email || '').trim().toLowerCase();
    const cleanPass = (password || '').trim();

    if (!cleanName || !cleanEmail || !cleanPass) {
      return { ok: false, error: 'Todos los campos son obligatorios.' };
    }
    if (cleanPass.length < 8) {
      return { ok: false, error: 'La contraseña debe tener un mínimo de 8 caracteres.' };
    }

    const yaFijo = USUARIOS_FICTICIOS.find((u) => u.email.toLowerCase() === cleanEmail);
    if (yaFijo) {
      return { ok: false, error: 'Ese correo electrónico ya está registrado en el sistema.' };
    }

    try {
      await usuariosApi.registrar(cleanName, cleanEmail, cleanPass);
      const tokenResponse = await usuariosApi.login(cleanEmail, cleanPass);
      const userData = await sesionDesdeToken(tokenResponse, cleanEmail, cleanName);
      return { ok: true, user: userData };
    } catch (err) {
      if (!(err instanceof ApiError) || !err.network) {
        return {
          ok: false,
          error: err.status === 409 ? 'Ese correo electrónico ya está registrado.' : err.message,
        };
      }
    }

    // Sin conexión con el backend: registro local (modo prueba)
    let regUsers;
    try {
      regUsers = JSON.parse(localStorage.getItem(LS_REG_KEY) || '[]');
    } catch {
      regUsers = [];
    }

    if (regUsers.some((u) => u.email.toLowerCase() === cleanEmail)) {
      return { ok: false, error: 'Ese correo ya está registrado.' };
    }

    const newUser = { name: cleanName, email: cleanEmail, password: cleanPass, rol: rol || 'USER' };
    regUsers.push(newUser);
    localStorage.setItem(LS_REG_KEY, JSON.stringify(regUsers));

    const userData = guardar({ name: newUser.name, email: newUser.email, rol: newUser.rol, demo: true });
    return { ok: true, user: userData };
  }

  function updateUser(newData) {
    if (!user) return null;
    const updated = { ...user, ...newData };
    localStorage.setItem(LS_KEY, JSON.stringify(updated));
    try {
      const regUsers = JSON.parse(localStorage.getItem(LS_REG_KEY) || '[]');
      const idx = regUsers.findIndex((u) => u.email.toLowerCase() === user.email.toLowerCase());
      if (idx !== -1) {
        regUsers[idx] = { ...regUsers[idx], ...newData };
        localStorage.setItem(LS_REG_KEY, JSON.stringify(regUsers));
      }
    } catch {
      // ignorar
    }
    setUser(updated);
    return updated;
  }

  function logout() {
    localStorage.removeItem(LS_KEY);
    borrarTokenInvitado();
    setUser(null);
  }

  /** Token JWT del usuario conectado (null si es de prueba o ha caducado) */
  function tokenUsuario() {
    return user && tokenVigente(user.token, user.tokenExp) ? user.token : null;
  }

  /** Token para hablar con el backend: el del usuario o, si no hay, el de invitado */
  function tokenActivo() {
    return tokenUsuario() || tokenInvitadoGuardado();
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: Boolean(user),
        login,
        register,
        logout,
        updateUser,
        tokenUsuario,
        tokenActivo,
        obtenerTokenInvitado,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

