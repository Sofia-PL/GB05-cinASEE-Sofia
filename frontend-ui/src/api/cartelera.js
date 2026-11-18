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

/** Fecha local en formato AAAA-MM-DD (sin pasar a UTC, que cambiaría el día) */
export function fechaIso(fecha) {
  const d = new Date(fecha);
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/**
 * Cine del frontend en el que se venden las sesiones reales.
 * El Comp. 1 no tiene el concepto de "cine" (solo salas y sesiones), así que su
 * cartelera se asigna a un único cine; los demás muestran sesiones de demostración.
 * Se cambia con VITE_CINE_CARTELERA (id de src/assets/cines.json).
 */
export const CINE_CARTELERA = Number(import.meta.env.VITE_CINE_CARTELERA || 1);

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
  };
}

/** Nombre legible del tipo de sala de Cartelera */
export const TIPOS_SALA = {
  ESTANDAR_2D: '2D',
  DIGITAL_3D: '3D',
  IMAX: 'IMAX',
  VIP: 'VIP',
  '4DX': '4DX',
};
