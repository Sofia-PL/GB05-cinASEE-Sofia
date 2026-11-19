/* =========================================================
   Componente 4 · Analítica (métricas e informes)
   A través del gateway: /api/metrics/** y /api/reports/**
   Solo STAFF_TAQUILLA o ADMIN.
   ========================================================= */
import { apiFetch } from './http';

/**
 * GET /api/metrics/occupancy -> { cinemaId, screenId, dayOfWeek, timeRange,
 *   totalCapacity, totalTicketsSold, occupancyPercentage }
 * @param {object} filtros { cinemaId (obligatorio), screenId?, dayOfWeek? (1=lunes), startTime?, endTime? }
 */
export function obtenerOcupacion(filtros, token) {
  return apiFetch('/api/metrics/occupancy', { token, query: filtros });
}

/**
 * GET /api/reports/box-office -> { movieId, movieTitle, period, totalTicketsSold, grossRevenue, currency }
 * @param {object} filtros { movieId?, startDate? (AAAA-MM-DD), endDate? }
 */
export function obtenerTaquilla(filtros, token) {
  return apiFetch('/api/reports/box-office', { token, query: filtros });
}

/**
 * GET /api/recommendations/users/{id}?limit= (el propio usuario o STAFF/ADMIN)
 * -> { userId, recommendations: [{ movieId, title, matchScore, reason }] }
 */
export function obtenerRecomendaciones(userId, token, limit = 4) {
  return apiFetch(`/api/recommendations/users/${userId}`, { token, query: { limit } });
}
