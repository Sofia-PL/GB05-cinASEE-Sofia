/* =========================================================
   Componente 1 · Cartelera (películas, salas y sesiones)
   A través del gateway: /api/v1/movies|rooms|sessions
   ========================================================= */
import { apiFetch } from './http';

/** GET /api/v1/movies (público) */
export function obtenerPeliculas() {
  return apiFetch('/api/v1/movies');
}

/** GET /api/v1/movies/{id} (público) */
export function obtenerPelicula(movieId) {
  return apiFetch(`/api/v1/movies/${movieId}`);
}

/**
 * GET /api/v1/sessions?date=AAAA-MM-DD (público)
 * @param {Date} fecha día del que se quieren las sesiones
 */
export function obtenerSesiones(fecha) {
  return apiFetch('/api/v1/sessions', { query: { date: fechaIso(fecha) } });
}

/** GET /api/v1/rooms (requiere token) -> todas las salas */
export function obtenerSalas(token) {
  return apiFetch('/api/v1/rooms', { token });
}

/** GET /api/v1/rooms/{id} (requiere token) -> filas y columnas de la sala */
export function obtenerSala(roomId, token) {
  return apiFetch(`/api/v1/rooms/${roomId}`, { token });
}

/* ---------------------------------------------------------
   Gestión (administración). Lectura de películas, sesiones y
   cines es pública; el resto exige token.
   --------------------------------------------------------- */

/** GET /api/v1/cinemas (público) */
export function obtenerCines() {
  return apiFetch('/api/v1/cinemas');
}

/** PUT /api/v1/cinemas/{id} · body { name, address, city, phone } */
export function actualizarCine(cinemaId, datos, token) {
  return apiFetch(`/api/v1/cinemas/${cinemaId}`, { method: 'PUT', body: datos, token });
}

/** POST /api/v1/cinemas · body { name, address, city, phone } */
export function crearCine(datos, token) {
  return apiFetch('/api/v1/cinemas', { method: 'POST', body: datos, token });
}

/** DELETE /api/v1/cinemas/{id} */
export function eliminarCine(cinemaId, token) {
  return apiFetch(`/api/v1/cinemas/${cinemaId}`, { method: 'DELETE', token });
}

/** GET /api/v1/movies?inShowcase=true (público) -> películas con sesiones futuras no canceladas */
export function obtenerPeliculasEnCartelera() {
  return apiFetch('/api/v1/movies', { query: { inShowcase: true } });
}

/** POST /api/v1/movies · body { title, synopsis, durationMinutes, genre, ageRating, posterUrl, trailerUrl } */
export function crearPelicula(datos, token) {
  return apiFetch('/api/v1/movies', { method: 'POST', body: datos, token });
}

/** PUT /api/v1/movies/{id} (mismos campos que al crear) */
export function actualizarPelicula(movieId, datos, token) {
  return apiFetch(`/api/v1/movies/${movieId}`, { method: 'PUT', body: datos, token });
}

/** DELETE /api/v1/movies/{id} */
export function eliminarPelicula(movieId, token) {
  return apiFetch(`/api/v1/movies/${movieId}`, { method: 'DELETE', token });
}

/** GET /api/v1/rooms?cinemaId= (requiere token) -> salas de un cine */
export function obtenerSalasCine(cinemaId, token) {
  return apiFetch('/api/v1/rooms', { token, query: { cinemaId } });
}

/** POST /api/v1/rooms · body { cinemaId, name, roomType, rowsCount, columnsCount, wheelchairAccessible, basePrice } */
export function crearSala(datos, token) {
  return apiFetch('/api/v1/rooms', { method: 'POST', body: datos, token });
}

/** PUT /api/v1/rooms/{id} · body { cinemaId, name, roomType, rowsCount, columnsCount, wheelchairAccessible, basePrice } */
export function actualizarSala(roomId, datos, token) {
  return apiFetch(`/api/v1/rooms/${roomId}`, { method: 'PUT', body: datos, token });
}

/** DELETE /api/v1/rooms/{id} */
export function eliminarSala(roomId, token) {
  return apiFetch(`/api/v1/rooms/${roomId}`, { method: 'DELETE', token });
}

/** GET /api/v1/sessions (público, sin filtros) -> todas las sesiones */
export function obtenerTodasSesiones() {
  return apiFetch('/api/v1/sessions');
}

/** POST /api/v1/sessions · body { movieId, roomId, startTime, ticketPrice } (se crea PROGRAMADA) */
export function crearSesion(datos, token) {
  return apiFetch('/api/v1/sessions', { method: 'POST', body: datos, token });
}

/** POST /api/v1/sessions/batch · body { movieId, roomId, daysOfWeek, startDate, endDate, startTime, ticketPrice } */
export function crearSesionesLote(datos, token) {
  return apiFetch('/api/v1/sessions/batch', { method: 'POST', body: datos, token });
}

/** PATCH /api/v1/sessions/{id} · body { roomId?, startTime?, status? } */
export function actualizarSesion(sessionId, cambios, token) {
  return apiFetch(`/api/v1/sessions/${sessionId}`, { method: 'PATCH', body: cambios, token });
}

/** Estados de una sesión en Cartelera. Reservas solo vende las que están A_LA_VENTA. */
export const ESTADOS_SESION = {
  PROGRAMADA: 'Programada',
  A_LA_VENTA: 'A la venta',
  EN_CURSO: 'En curso',
  FINALIZADA: 'Finalizada',
  CANCELADA: 'Cancelada',
};

/** Fecha local en formato AAAA-MM-DD (sin pasar a UTC, que cambiaría el día) */
export function fechaIso(fecha) {
  const d = new Date(fecha);
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/** GET /api/v1/sessions/{id} (requiere token) */
export function obtenerSesion(sessionId, token) {
  return apiFetch(`/api/v1/sessions/${sessionId}`, { token });
}

/** GET /api/v1/cinemas/{id} (público) */
export function obtenerCine(cinemaId) {
  return apiFetch(`/api/v1/cinemas/${cinemaId}`);
}

/** "2026-11-10T19:00:00" (LocalDateTime de Cartelera, hora local del cine) -> Date */
export function fechaSesion(startTime) {
  return new Date(String(startTime || ''));
}

/** "2026-11-10T19:00:00" -> "19:00" */
export function horaSesion(startTime) {
  return String(startTime || '').slice(11, 16);
}

/**
 * Cartelera de un cine para un día: sesiones A_LA_VENTA que aún no han empezado,
 * en las salas de ese cine, agrupadas por película.
 * Leer las salas exige token (el del usuario o uno de invitado).
 * @returns {{ peliculas: object[], sesionesPorPelicula: Record<string, object[]> }}
 */
export async function cargarCarteleraCine(cinemaId, fecha, token) {
  const [salas, sesiones, pelis] = await Promise.all([
    obtenerSalasCine(cinemaId, token),
    obtenerSesiones(fecha),
    obtenerPeliculas(),
  ]);
  const salaPorId = new Map((salas || []).map((s) => [s.id, s]));
  const ahora = Date.now();

  const porPelicula = {};
  (sesiones || [])
    .filter((s) => s.status === 'A_LA_VENTA' && salaPorId.has(s.roomId) && fechaSesion(s.startTime).getTime() > ahora)
    .sort((a, b) => String(a.startTime).localeCompare(String(b.startTime)))
    .forEach((s) => {
      (porPelicula[s.movieId] ||= []).push(adaptarSesion(s, salaPorId.get(s.roomId)));
    });

  const catalogo = new Map((pelis || []).map((m) => [m.id, m]));
  const peliculas = Object.keys(porPelicula)
    .filter((movieId) => catalogo.has(movieId))
    .map((movieId) => adaptarPelicula(catalogo.get(movieId)));
  return { peliculas, sesionesPorPelicula: porPelicula };
}

/** GET /api/v1/sessions?movieId= (público) -> todas las sesiones de una película */
export function obtenerSesionesPelicula(movieId) {
  return apiFetch('/api/v1/sessions', { query: { movieId } });
}

/**
 * Pases de una película: sesiones A_LA_VENTA que aún no han empezado, en cualquier cine,
 * ordenadas por hora. Cada pase lleva su cinemaId y su día (AAAA-MM-DD) para agruparlos.
 * Leer las salas exige token (el del usuario o uno de invitado).
 */
export async function cargarSesionesPelicula(movieId, token) {
  const [salas, sesiones] = await Promise.all([
    obtenerSalas(token),
    obtenerSesionesPelicula(movieId),
  ]);
  const salaPorId = new Map((salas || []).map((s) => [s.id, s]));
  const ahora = Date.now();

  return (sesiones || [])
    .filter((s) => s.status === 'A_LA_VENTA' && salaPorId.has(s.roomId) && fechaSesion(s.startTime).getTime() > ahora)
    .sort((a, b) => String(a.startTime).localeCompare(String(b.startTime)))
    .map((s) => {
      const sala = salaPorId.get(s.roomId);
      return { ...adaptarSesion(s, sala), cinemaId: sala.cinemaId, dia: fechaIso(fechaSesion(s.startTime)) };
    });
}

/** Sesión de Cartelera en el formato que usan las fichas y la compra */
function adaptarSesion(s, sala) {
  return {
    showId: s.id,                // id de la sesión en Cartelera = showId en Reservas
    roomId: s.roomId,
    startTime: s.startTime,
    hora: horaSesion(s.startTime),
    salaNombre: sala.name || '',
    formato: TIPOS_SALA[sala.roomType] || sala.roomType || '',
    precio: Number(s.ticketPrice) || 0,
  };
}

/** Película de Cartelera con valores por defecto para los campos opcionales */
export function adaptarPelicula(m) {
  return {
    id: m.id,
    title: m.title || `Película ${m.id}`,
    synopsis: m.synopsis || '',
    durationMinutes: m.durationMinutes || 0,
    genre: m.genre || '',
    ageRating: m.ageRating || '',
    posterUrl: m.posterUrl || '',
    trailerUrl: m.trailerUrl || '',
  };
}

/** Nombre legible de los géneros (Cartelera los guarda en mayúsculas y sin tildes) */
export const GENEROS = {
  ACCION: 'Acción',
  CIENCIA_FICCION: 'Ciencia Ficción',
  DRAMA: 'Drama',
  COMEDIA: 'Comedia',
  TERROR: 'Terror',
  ANIMACION: 'Animación',
  FANTASIA: 'Fantasía',
  THRILLER: 'Thriller',
  ROMANCE: 'Romance',
};

export function nombreGenero(genero) {
  return GENEROS[genero] || String(genero || '').replace(/_/g, ' ');
}

/** Nombre legible del tipo de sala de Cartelera */
export const TIPOS_SALA = {
  ESTANDAR_2D: '2D',
  DIGITAL_3D: '3D',
  IMAX: 'IMAX',
  VIP: 'VIP',
  '4DX': '4DX',
};
