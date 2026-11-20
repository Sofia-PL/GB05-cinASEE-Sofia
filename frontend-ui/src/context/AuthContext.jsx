import { useState, useEffect, useCallback } from 'react';
import { leerJwt, alRechazarseToken } from '../api/http';
import * as usuariosApi from '../api/usuarios';
import { AuthContext } from './useAuth';

/* =========================================================
   Sesión del usuario (Componente 3 · Usuarios)
   ---------------------------------------------------------
   El login y el registro van siempre contra el backend. Lo único
   que se guarda en el navegador es la sesión (token JWT y datos
   básicos del perfil) para no tener que entrar en cada recarga.
   ========================================================= */

const LS_KEY = 'cinasee_user';

const { tokenVigente, tokenInvitadoGuardado, obtenerTokenInvitado, borrarTokenInvitado } = usuariosApi;

/* Roles del Componente 3 -> roles que usan las vistas del frontend */
const ROLES_FRONT = {
  CLIENTE_REGISTRADO: 'USER',
  STAFF_TAQUILLA: 'STAFF',
  ADMIN: 'ADMIN',
  VISITANTE: 'USER',
};

/** Sesión guardada, solo si su token sigue vigente */
function cargarUsuario() {
  try {
    const u = JSON.parse(localStorage.getItem(LS_KEY) || 'null');
    if (u && tokenVigente(u.token, u.tokenExp)) {
      if (!u.id && u.token) {
        const claims = leerJwt(u.token);
        if (claims?.sub) u.id = Number(claims.sub);
      }
      return u;
    }
    localStorage.removeItem(LS_KEY);
  } catch {
    // almacenamiento no disponible: sin sesión
  }
  return null;
}

/** Datos del perfil del Comp. 3 que usa el frontend */
function datosPerfil(perfil) {
  return {
    id: perfil.id,
    name: perfil.fullName,
    email: perfil.email,
    phone: perfil.phone || '',
    homeCinema: perfil.homeCinema || '',
    preferredGenres: perfil.preferredGenres || [],
    loyaltyPoints: perfil.loyaltyPoints ?? 0,
    cinemaId: perfil.cinemaId || null,
    canReviewRoleRequests: Boolean(perfil.canReviewRoleRequests || perfil.role === 'ADMIN'),
  };
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(cargarUsuario);
  // true cuando la sesión se ha cerrado sola por token caducado o rechazado (lo muestra el login)
  const [sesionCaducada, setSesionCaducada] = useState(false);

  /** Cierra la sesión porque su token ya no vale, y lo deja indicado para avisar en el login */
  const cerrarSesionCaducada = useCallback(() => {
    try {
      localStorage.removeItem(LS_KEY);
    } catch {
      // ignorar
    }
    setUser(null);
    setSesionCaducada(true);
  }, []);

  /* Cierre automático al caducar el token (con el mismo margen de 30 s que tokenVigente) */
  useEffect(() => {
    if (!user?.tokenExp) return undefined;
    const ms = Math.max(0, user.tokenExp * 1000 - Date.now() - 30_000);
    const timer = window.setTimeout(cerrarSesionCaducada, ms);
    return () => window.clearTimeout(timer);
  }, [user?.tokenExp, cerrarSesionCaducada]);

  /* Cierre si el backend rechaza el token del usuario (401), p. ej. tras reiniciar el Comp. 3 */
  useEffect(() => {
    if (!user?.token) return undefined;
    const tokenSesion = user.token;
    return alRechazarseToken((tokenRechazado) => {
      if (tokenRechazado === tokenSesion) cerrarSesionCaducada();
    });
  }, [user?.token, cerrarSesionCaducada]);

  function guardar(userData) {
    try {
      localStorage.setItem(LS_KEY, JSON.stringify(userData));
    } catch {
      // sin almacenamiento: la sesión dura lo que la pestaña
    }
    setUser(userData);
    setSesionCaducada(false);
    return userData;
  }

  /** Construye la sesión a partir del token del Comp. 3 y el perfil del usuario */
  async function sesionDesdeToken(tokenResponse) {
    const token = tokenResponse.accessToken;
    const claims = leerJwt(token) || {};
    const backendRole = tokenResponse.role || claims.role;
    const id = Number(claims.sub);
    const perfil = await usuariosApi.obtenerPerfil(id, token);
    return guardar({
      id,
      ...datosPerfil(perfil),
      rol: ROLES_FRONT[backendRole] || 'USER',
      backendRole,
      token,
      tokenExp: claims.exp,
    });
  }

  /** POST /auth/login + GET /users/{id} */
  async function login(email, password) {
    const normEmail = (email || '').trim().toLowerCase();
    const normPass = (password || '').trim();
    if (!normEmail || !normPass) {
      return { ok: false, error: 'Por favor, introduce tu correo electrónico y contraseña.' };
    }
    try {
      const tokenResponse = await usuariosApi.login(normEmail, normPass);
      return { ok: true, user: await sesionDesdeToken(tokenResponse) };
    } catch (err) {
      const credenciales = err.status === 400 || err.status === 401 || err.status === 404;
      return { ok: false, error: credenciales ? 'Correo electrónico o contraseña incorrectos.' : err.message };
    }
  }

  /** POST /auth/register y login automático después */
  async function register(name, email, password) {
    const cleanName = (name || '').trim();
    const cleanEmail = (email || '').trim().toLowerCase();
    const cleanPass = (password || '').trim();

    if (!cleanName || !cleanEmail || !cleanPass) {
      return { ok: false, error: 'Todos los campos son obligatorios.' };
    }
    if (cleanPass.length < 8) {
      return { ok: false, error: 'La contraseña debe tener un mínimo de 8 caracteres.' };
    }
    try {
      await usuariosApi.registrar(cleanName, cleanEmail, cleanPass);
      const tokenResponse = await usuariosApi.login(cleanEmail, cleanPass);
      return { ok: true, user: await sesionDesdeToken(tokenResponse) };
    } catch (err) {
      return {
        ok: false,
        error: err.status === 409 ? 'Ese correo electrónico ya está registrado.' : err.message,
      };
    }
  }

  /**
   * Guarda cambios del perfil en el Comp. 3 (PUT /users/{id}) y vuelve a leerlo.
   * @param {object} cambios { fullName?, phone?, homeCinema?, preferredGenres? }
   */
  async function actualizarPerfil(cambios) {
    if (!user) throw new Error('No hay sesión iniciada.');
    await usuariosApi.actualizarPerfil(user.id, cambios, user.token);
    const perfil = await usuariosApi.obtenerPerfil(user.id, user.token);
    return guardar({ ...user, ...datosPerfil(perfil) });
  }

  function logout() {
    try {
      localStorage.removeItem(LS_KEY);
    } catch {
      // ignorar
    }
    borrarTokenInvitado();
    setUser(null);
    setSesionCaducada(false);
  }

  /** Token JWT del usuario conectado (null si no hay sesión o ha caducado) */
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
        actualizarPerfil,
        tokenUsuario,
        tokenActivo,
        obtenerTokenInvitado,
        sesionCaducada,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
