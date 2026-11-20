import EntradaTicket from './entrada/EntradaTicket';

/* =========================================================
   Entradas en formato de impresión
   ---------------------------------------------------------
   No se ve en pantalla: solo aparece al imprimir (ver los estilos
   @media print de index.css, que ocultan el resto de la página).
   Usa el mismo diseño de entrada que se ve en pantalla y se descarga.
   Lo usan la confirmación del pago y "Mis entradas".
   ========================================================= */

/**
 * @param {object[]} entradas datos de construirEntrada() (components/entrada/datosEntrada.js)
 */
export default function EntradasImprimibles({ entradas }) {
  if (!entradas || entradas.length === 0) return null;
  return (
    <div className="entradas-impresion" aria-hidden="true">
      {entradas.map((e) => (
        <div key={e.codigo} className="entrada-impresa">
          <EntradaTicket entrada={e} descargable={false} ancho={300} />
        </div>
      ))}
    </div>
  );
}
