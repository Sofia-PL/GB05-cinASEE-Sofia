/* Selector de días de proyección (hoy + 6 días), compartido por las fichas de cine y de película */

const DIAS_SEMANA = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/** Los próximos `cantidad` días a las 00:00, empezando por hoy */
export function generarDias(cantidad = 7) {
  const dias = [];
  const hoy = new Date();
  hoy.setHours(0, 0, 0, 0);
  for (let i = 0; i < cantidad; i++) {
    const d = new Date(hoy);
    d.setDate(hoy.getDate() + i);
    dias.push(d);
  }
  return dias;
}

/** Etiqueta del día: "Hoy", "Mañana" o el día de la semana, con la fecha corta debajo */
export function formatDia(d, index) {
  const sub = `${d.getDate()} ${MESES[d.getMonth()]}`;
  if (index === 0) return { label: 'Hoy', sub };
  if (index === 1) return { label: 'Mañana', sub };
  return { label: DIAS_SEMANA[d.getDay()], sub };
}
