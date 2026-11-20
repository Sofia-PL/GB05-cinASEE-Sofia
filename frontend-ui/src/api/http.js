/* =========================================================
   Cliente HTTP común para hablar con el backend
   ---------------------------------------------------------
   Todas las peticiones van al API Gateway (Componente 5), que
   las reparte a cada microservicio:
     /api/v1/movies|rooms|sessions  -> Comp. 1 Cartelera
     /v1/reservations/**            -> Comp. 2 Reservas y pagos
     /v1/iam/**                     -> Comp. 3 Usuarios
     /api/metrics|reports/**        -> Comp. 4 Analítica

   La dirección del gateway se configura con VITE_API_URL
   (fichero .env, ver .env.example). Por defecto: localhost:8080.
   ========================================================= */

export const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '');

/**
 * Error de la API con la información que devuelven los microservicios
 * (Reservas devuelve { code, message, details }).
 * - status: código HTTP (0 si no se pudo conectar).
 * - network: true si el gateway no responde (backend apagado).
 */
export class ApiError extends Error {
  constructor({ status = 0, code = '', message = '', details = [], network = false }) {
    super(message || 'Error de comunicación con el servidor.');
    this.status = status;
    this.code = code;
    this.details = details;
    this.network = network;
  }
}

/*
 * Aviso de token rechazado: si el backend responde 401 a una petición que llevaba
 * token, ese token ya no vale (caducado, o el Comp. 3 se reinició y cambió sus claves).
 * El contexto de sesión se suscribe para cerrar la sesión del usuario.
 */
let avisoTokenRechazado = null;

/** Registra la función que se llama con el token rechazado. Devuelve la función para darse de baja. */
export function alRechazarseToken(fn) {
  avisoTokenRechazado = fn;
  return () => {
    if (avisoTokenRechazado === fn) avisoTokenRechazado = null;
  };
}

/**
 * Hace una petición JSON al gateway.
 * @param {string} path   ruta a partir del gateway, p. ej. "/v1/reservations"
 * @param {object} opts   { method, body, token, query }
 * @returns el cuerpo JSON de la respuesta (o null si viene vacío)
 */
export async function apiFetch(path, { method = 'GET', body, token, query } = {}) {
  let url = `${API_URL}${path}`;
  if (query) {
    const qs = new URLSearchParams(
      Object.entries(query).filter(([, v]) => v !== undefined && v !== null && v !== '')
    ).toString();
    if (qs) url += `?${qs}`;
  }

  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    // fetch solo falla así cuando no hay conexión (gateway apagado, CORS, red...)
    throw new ApiError({
      network: true,
      message: 'No se puede conectar con el servidor de CinASEE. Comprueba que el gateway y los microservicios están arrancados.',
    });
  }

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }

  if (!response.ok) {
    if (response.status === 401 && token && avisoTokenRechazado) avisoTokenRechazado(token);
    const info = data && typeof data === 'object' ? data : {};
    throw new ApiError({
      status: response.status,
      code: info.code || info.error || '',
      message: info.message || mensajePorEstado(response.status),
      details: Array.isArray(info.details) ? info.details : [],
    });
  }
  return data;
}

/** Mensaje en castellano para los códigos HTTP más habituales */
function mensajePorEstado(status) {
  switch (status) {
    case 400: return 'La petición no es válida.';
    case 401: return 'Tu sesión ha caducado o no has iniciado sesión.';
    case 403: return 'No tienes permiso para realizar esta acción.';
    case 404: return 'No se ha encontrado el recurso solicitado.';
    case 409: return 'La operación entra en conflicto con el estado actual.';
    case 503: return 'El servicio no está disponible en este momento. Inténtalo de nuevo en unos segundos.';
    default: return `Error del servidor (HTTP ${status}).`;
  }
}

/**
 * Lee el contenido (payload) de un token JWT sin validarlo.
 * La validación real la hacen el gateway y cada microservicio;
 * aquí solo se usa para conocer el id del usuario (sub), su rol y la caducidad.
 */
export function leerJwt(token) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(payload)
        .split('')
        .map((c) => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join('')
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}
