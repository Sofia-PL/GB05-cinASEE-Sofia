/* =========================================================
   Utilidades de butacas para salas reales de Cartelera (Comp. 1)
   ---------------------------------------------------------
   Una butaca se identifica en pantalla por su índice dentro de la
   matriz (fila * columnas + columna, base 0) y en Reservas por
   { row, column } (base 1).
   ========================================================= */
import { TIPOS_SALA } from '../api/cartelera';

/** Matriz y textos de una sala de Cartelera */
export function layoutDeSala(room) {
  const formato = TIPOS_SALA[room.roomType] || room.roomType || '';
  return {
    filas: room.rowsCount,
    cols: room.columnsCount,
    tipo: room.name || 'Sala',
    formato,
    descripcion: [formato && `Formato ${formato}`, room.wheelchairAccessible && 'accesible en silla de ruedas']
      .filter(Boolean)
      .join(', ') || 'Sala de proyección',
  };
}

/** Índice de pantalla -> { row, column } que espera Reservas */
export function butacaDeIndice(idx, cols) {
  return { row: Math.floor(idx / cols) + 1, column: (idx % cols) + 1 };
}

/** Butacas ocupadas que devuelve Reservas -> conjunto de índices de pantalla */
export function indicesOcupados(occupiedSeats, cols) {
  return new Set((occupiedSeats || []).map((s) => (s.row - 1) * cols + (s.column - 1)));
}

/** Índice de pantalla -> "D7" */
export function etiquetaButaca(idx, cols) {
  return `${String.fromCharCode(65 + Math.floor(idx / cols))}${(idx % cols) + 1}`;
}
