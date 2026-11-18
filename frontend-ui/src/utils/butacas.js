/* =========================================================
   Utilidades de butacas para las sesiones de DEMOSTRACIÓN
   (las sesiones reales usan las filas/columnas de la sala del Comp. 1
   y las butacas ocupadas que devuelve Reservas, Comp. 2).
   Se usan en la compra, en la taquilla y en la administración de salas.
   ========================================================= */

/* Matriz de butacas personalizada por CINE y por SALA */
export function getSeatLayout(cineId, sala) {
  const c = Number(cineId) || 1;
  const s = Number(sala) || 1;

  if (s === 5 || (c <= 2 && s === 1)) {
    return {
      filas: 12,
      cols: 16,
      tipo: 'Sala IMAX Gran Pantalla',
      descripcion: 'Proyección Láser 4K y Sonido Inmersivo',
    };
  }

  if (s === 4) {
    return {
      filas: 6,
      cols: 8,
      tipo: 'Sala VIP Luxury',
      descripcion: 'Butacas reclinables y mayor espacio individual',
    };
  }

  if (s === 3) {
    const filas = 9 + (c % 2);
    const cols = 12 + (c % 3);
    return {
      filas,
      cols,
      tipo: 'Sala 3D RealD',
      descripcion: 'Proyección tridimensional de alta definición',
    };
  }

  const filas = 8 + ((c + s) % 3);
  const cols = 10 + ((c * 2 + s) % 4);
  return {
    filas,
    cols,
    tipo: `Sala ${s} Estándar`,
    descripcion: 'Sonido digital envolvente y butacas ergonómicas',
  };
}

export function getOcupadasSet(cineId, peliculaId, sala, hora) {
  const { filas, cols } = getSeatLayout(cineId, sala);
  const total = filas * cols;
  const horaNum = parseInt((hora || '16:00').replace(':', ''), 10);
  const seed = (Number(cineId) * 23 + Number(peliculaId) * 17 + Number(sala) * 11 + horaNum) % 10000;

  const ocupadas = new Set();
  let s = seed;
  const cantOcupadas = Math.floor(total * 0.28);
  while (ocupadas.size < cantOcupadas) {
    s = (s * 1664525 + 1013904223) % (2 ** 32);
    ocupadas.add(Math.abs(s) % total);
  }
  return ocupadas;
}
