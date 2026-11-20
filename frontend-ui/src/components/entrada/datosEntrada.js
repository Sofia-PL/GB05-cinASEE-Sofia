import { nombreGenero } from '../../api/cartelera';
import { duracionTexto } from './temasEntrada';

const ZONA = { timeZone: 'Europe/Madrid' };

/**
 * Datos que dibuja una entrada, a partir de lo que devuelven Reservas (Comp. 2) y
 * Cartelera (Comp. 1). La fecha se muestra en la hora del cine (Europe/Madrid).
 *
 * @param {object} p
 * @param {string} p.codigo     código de la entrada (QR-{reserva}-{fila}-{columna}-{sufijo})
 * @param {object} [p.pelicula] película de Cartelera (title, genre, ageRating, durationMinutes)
 * @param {string} [p.cine]     nombre del cine
 * @param {object} [p.sala]     sala de Cartelera (name, roomType, rowsCount, columnsCount)
 * @param {string} [p.inicio]   showStartTime de Reservas (instante ISO)
 * @param {number} [p.precio]   precio de esa butaca
 * @param {string} [p.descuento] NONE | SPECTATOR_DAY | LOYALTY
 * @param {number} [p.reserva]  número de reserva
 */
export function construirEntrada({ codigo, pelicula, cine, sala, inicio, precio, descuento, reserva }) {
  const fecha = inicio ? new Date(inicio) : null;
  const valida = fecha && !Number.isNaN(fecha.getTime());
  const butaca = /^QR-\d+-(\d+)-(\d+)-/.exec(String(codigo || ''));
  return {
    codigo,
    reserva,
    titulo: pelicula?.title || 'Película',
    genero: pelicula?.genre ? nombreGenero(pelicula.genre) : '',
    edad: pelicula?.ageRating || '',
    duracion: duracionTexto(pelicula?.durationMinutes),
    cine: cine || '',
    salaNombre: sala?.name || 'Sala',
    roomType: sala?.roomType,
    filas: Number(sala?.rowsCount) || 0,
    columnas: Number(sala?.columnsCount) || 0,
    fila: butaca ? Number(butaca[1]) : null,
    columna: butaca ? Number(butaca[2]) : null,
    fechaTexto: valida
      ? fecha.toLocaleDateString('es-ES', { ...ZONA, weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })
      : '',
    hora: valida ? fecha.toLocaleTimeString('es-ES', { ...ZONA, hour: '2-digit', minute: '2-digit' }) : '',
    diaSemana: valida ? fecha.toLocaleDateString('es-ES', { ...ZONA, weekday: 'long' }) : '',
    precio: precio !== undefined && precio !== null ? Number(precio) : null,
    descuento: descuento || 'NONE',
  };
}
