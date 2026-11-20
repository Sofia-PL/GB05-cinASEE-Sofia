/* =========================================================
   Aspecto de las entradas según el tipo de sala y el tipo de entrada
   ========================================================= */

/** Un tema por tipo de sala (roomType de Cartelera): colores de la cabecera y de los detalles */
export const TEMAS_SALA = {
  ESTANDAR_2D: {
    formato: '2D',
    nombre: 'Sala clásica',
    principal: '#7a1f2b',
    papel: '#fbf3e6',
    tinta: '#3b0d14',
    acento: '#d9a441',
  },
  DIGITAL_3D: {
    formato: '3D',
    nombre: 'Digital 3D',
    principal: '#0f766e',
    papel: '#effaf8',
    tinta: '#083b37',
    acento: '#ec4899',
  },
  IMAX: {
    formato: 'IMAX',
    nombre: 'Experiencia IMAX',
    principal: '#0b1f4b',
    papel: '#eef3ff',
    tinta: '#0b1f4b',
    acento: '#38bdf8',
  },
  VIP: {
    formato: 'VIP',
    nombre: 'Sala VIP',
    principal: '#141414',
    papel: '#fffaf0',
    tinta: '#1a1a1a',
    acento: '#c9a227',
  },
  '4DX': {
    formato: '4DX',
    nombre: 'Experiencia 4DX',
    principal: '#c2410c',
    papel: '#fff4ec',
    tinta: '#431407',
    acento: '#facc15',
  },
};

export function temaDeSala(roomType) {
  return TEMAS_SALA[roomType] || TEMAS_SALA.ESTANDAR_2D;
}

/** Cintas que se añaden según el tipo de entrada (descuento, día y hora de la sesión) */
export function cintasDeEntrada({ descuento, diaSemana, hora }) {
  const cintas = [];
  if (descuento === 'SPECTATOR_DAY') cintas.push({ texto: 'DÍA DEL ESPECTADOR −20 %', color: '#15803d' });
  if (descuento === 'LOYALTY') cintas.push({ texto: 'CLUB CINASEE −10 %', color: '#6d28d9' });
  if (Number(String(hora || '').slice(0, 2)) >= 22) cintas.push({ texto: '☾ SESIÓN DE NOCHE', color: '#1e1b4b' });
  if (diaSemana === 'sábado' || diaSemana === 'domingo') cintas.push({ texto: 'FIN DE SEMANA', color: '#be185d' });
  return cintas;
}

/** Parte un texto en líneas de como mucho `max` caracteres (sin cortar palabras), hasta `lineas` líneas */
export function partirTexto(texto, max, lineas) {
  const palabras = String(texto || '').split(/\s+/).filter(Boolean);
  const resultado = [];
  let actual = '';
  for (const palabra of palabras) {
    const prueba = actual ? `${actual} ${palabra}` : palabra;
    if (prueba.length <= max) {
      actual = prueba;
    } else {
      if (actual) resultado.push(actual);
      actual = palabra;
    }
  }
  if (actual) resultado.push(actual);
  if (resultado.length > lineas) {
    const recortado = resultado.slice(0, lineas);
    // Se deja margen para los puntos suspensivos, que no se salgan del borde
    recortado[lineas - 1] = `${recortado[lineas - 1].slice(0, max - 3).trimEnd()}…`;
    return recortado;
  }
  return resultado;
}

/** "101" -> "1 h 41 min" */
export function duracionTexto(minutos) {
  const m = Number(minutos) || 0;
  if (!m) return '';
  return m >= 60 ? `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min` : `${m} min`;
}
