/* =========================================================
   Componente 3 · Usuarios (identidad y tokens JWT)
   A través del gateway: /v1/iam/**
   ========================================================= */
import { apiFetch, leerJwt } from './http';

const SS_GUEST_KEY = 'cinasee_guest_token';
const LS_DEVICE_KEY = 'cinasee_device_id';

/** true si el token existe y le queda al menos medio minuto de vida */
export function tokenVigente(token, exp) {
  return Boolean(token) && Boolean(exp) && exp * 1000 > Date.now() + 30_000;
}

/** Identificador estable del navegador (lo pide el Comp. 3 para el token de invitado) */
function deviceId() {
  try {
    let id = localStorage.getItem(LS_DEVICE_KEY);
    if (!id) {
      id = `web-${crypto.randomUUID()}`;
      localStorage.setItem(LS_DEVICE_KEY, id);
    }
    return id;
  } catch {
    return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
}

/** Token de invitado ya obtenido en esta pestaña (null si no hay o caducó) */
export function tokenInvitadoGuardado() {
  try {
    const g = JSON.parse(sessionStorage.getItem(SS_GUEST_KEY) || 'null');
    return g && tokenVigente(g.token, g.exp) ? g.token : null;
  } catch {
    return null;
  }
}

/**
 * Token de VISITANTE (el guardado o uno nuevo). El Comp. 3 solo lo firma:
 * no crea usuarios ni guarda nada en su base de datos.
 */
export async function obtenerTokenInvitado() {
  const existente = tokenInvitadoGuardado();
  if (existente) return existente;
  const res = await tokenInvitado(deviceId());
  const claims = leerJwt(res.accessToken) || {};
  try {
    sessionStorage.setItem(SS_GUEST_KEY, JSON.stringify({ token: res.accessToken, exp: claims.exp }));
  } catch {
    // sin almacenamiento: se pedirá otro la próxima vez
  }
  return res.accessToken;
}

/**
 * Token para leer datos que exigen autenticación (p. ej. las salas de Cartelera):
 * el del usuario si lo hay o, si no, uno de invitado.
 */
export function tokenParaLeer(tokenUsuario) {
  return tokenUsuario ? Promise.resolve(tokenUsuario) : obtenerTokenInvitado();
}

export function borrarTokenInvitado() {
  try {
    sessionStorage.removeItem(SS_GUEST_KEY);
  } catch {
    // ignorar
  }
}

/** POST /v1/iam/auth/login -> { accessToken, tokenType, expiresIn, role } */
export function login(email, password) {
  return apiFetch('/v1/iam/auth/login', { method: 'POST', body: { email, password } });
}

/** POST /v1/iam/auth/register -> 201 sin cuerpo (409 si el correo ya existe) */
export function registrar(fullName, email, password) {
  return apiFetch('/v1/iam/auth/register', { method: 'POST', body: { fullName, email, password } });
}

/** POST /v1/iam/auth/guest -> token corto de VISITANTE para comprar sin cuenta */
export function tokenInvitado(deviceId) {
  return apiFetch('/v1/iam/auth/guest', { method: 'POST', body: { deviceId } });
}

/**
 * GET /v1/iam/users/{id} -> { id, email, fullName, role, loyaltyPoints,
 *   preferredGenres, phone, homeCinema, cinemaId, canReviewRoleRequests }
 */
export function obtenerPerfil(userId, token) {
  return apiFetch(`/v1/iam/users/${userId}`, { token });
}

/** PUT /v1/iam/users/{id} · body { fullName?, preferredGenres?, phone?, homeCinema? } */
export function actualizarPerfil(userId, datos, token) {
  return apiFetch(`/v1/iam/users/${userId}`, { method: 'PUT', body: datos, token });
}

/** GET /v1/iam/users/{id}/history -> [{ reservationId, movieId, purchaseDate, amount }] */
export function obtenerHistorial(userId, token) {
  return apiFetch(`/v1/iam/users/${userId}/history`, { token });
}

/** POST /v1/iam/role-requests -> crea una solicitud de rol */
export function crearSolicitudRol(datos, token) {
  return apiFetch('/v1/iam/role-requests', {
    method: 'POST',
    body: datos,
    token,
  });
}

/** GET /v1/iam/role-requests/me -> solicitudes del usuario actual */
export function obtenerMisSolicitudesRol(token) {
  return apiFetch('/v1/iam/role-requests/me', { token });
}

/** GET /v1/iam/role-requests -> listado para administradores/managers */
export function obtenerSolicitudesRol(filtros = {}, token) {
  return apiFetch('/v1/iam/role-requests', {
    query: filtros,
    token,
  });
}

/** PATCH /v1/iam/role-requests/{id} -> aprobar o rechazar */
export function revisarSolicitudRol(requestId, datos, token) {
  return apiFetch(`/v1/iam/role-requests/${requestId}`, {
    method: 'PATCH',
    body: datos,
    token,
  });
}

/** PUT /v1/iam/users/{id}/role -> asignar rol directamente por ID (solo ADMIN) */
export function asignarRolUsuario(userId, datos, token) {
  return apiFetch(`/v1/iam/users/${userId}/role`, {
    method: 'PUT',
    body: datos,
    token,
  });
}
