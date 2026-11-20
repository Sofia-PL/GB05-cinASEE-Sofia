import CodigoQR from './CodigoQR';
import { butacaDeCodigo } from '../api/reservas';

/* =========================================================
   Entradas en formato de impresión
   ---------------------------------------------------------
   No se ve en pantalla: solo aparece al imprimir (ver los estilos
   @media print de index.css, que ocultan el resto de la página).
   Lo usan la confirmación del pago y "Mis entradas".
   ========================================================= */

/**
 * @param {object[]} entradas [{ codigo, pelicula, cine, sala, fecha ("dd/mm/aaaa"), hora ("HH:MM"), butaca, reserva }]
 */
export default function EntradasImprimibles({ entradas }) {
  if (!entradas || entradas.length === 0) return null;
  return (
    <div className="entradas-impresion" aria-hidden="true">
      {entradas.map((e) => (
        <article key={e.codigo} className="entrada-impresa">
          <div className="entrada-impresa-datos">
            <p className="entrada-impresa-marca">CinASEE</p>
            <h2>{e.pelicula || 'Película'}</h2>
            <p><strong>Cine:</strong> {[e.cine, e.sala].filter(Boolean).join(' — ') || '—'}</p>
            <p><strong>Sesión:</strong> {[e.fecha, e.hora].filter(Boolean).join(' · ') || '—'}</p>
            <p><strong>Butaca:</strong> {e.butaca || butacaDeCodigo(e.codigo) || '—'}</p>
            {e.reserva && <p><strong>Reserva:</strong> nº {e.reserva}</p>}
            <code>{e.codigo}</code>
          </div>
          <CodigoQR texto={e.codigo} tamano={140} />
        </article>
      ))}
    </div>
  );
}
