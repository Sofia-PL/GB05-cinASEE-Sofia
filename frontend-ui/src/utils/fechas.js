/* Fechas en formato español (dd/mm/aaaa). Internamente se usa siempre AAAA-MM-DD,
   que es lo que esperan los microservicios. */

/** "2026-11-25" (o "2026-11-25T19:00:00") -> "25/11/2026" */
export function fechaEs(iso) {
  const [a, m, d] = String(iso || '').slice(0, 10).split('-');
  return a && m && d ? `${d}/${m}/${a}` : '';
}

/** "25/11/2026" -> "2026-11-25" (o '' si no es una fecha válida) */
export function isoDesdeFechaEs(texto) {
  const m = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(String(texto || '').trim());
  if (!m) return '';
  const [, d, mes, a] = m.map(Number);
  const fecha = new Date(a, mes - 1, d);
  if (fecha.getFullYear() !== a || fecha.getMonth() !== mes - 1 || fecha.getDate() !== d) return '';
  return `${a}-${String(mes).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

/** Mientras se escribe una fecha, añade las barras: "2511" -> "25/11" */
export function enmascararFecha(texto) {
  const digitos = String(texto || '').replace(/\D/g, '').slice(0, 8);
  if (digitos.length <= 2) return digitos;
  if (digitos.length <= 4) return `${digitos.slice(0, 2)}/${digitos.slice(2)}`;
  return `${digitos.slice(0, 2)}/${digitos.slice(2, 4)}/${digitos.slice(4)}`;
}

/** Mientras se escribe una hora, añade los dos puntos: "1930" -> "19:30" */
export function enmascararHora(texto) {
  const digitos = String(texto || '').replace(/\D/g, '').slice(0, 4);
  return digitos.length <= 2 ? digitos : `${digitos.slice(0, 2)}:${digitos.slice(2)}`;
}

/** "19:30" -> true si es una hora válida de 24 h */
export function horaValida(texto) {
  const m = /^(\d{2}):(\d{2})$/.exec(String(texto || ''));
  return Boolean(m) && Number(m[1]) < 24 && Number(m[2]) < 60;
}
