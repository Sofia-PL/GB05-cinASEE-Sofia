/* =========================================================
   Mapa de butacas de una sala (compra online y taquilla)
   ========================================================= */

/**
 * @param {object}   layout        { filas, cols }
 * @param {Set}      ocupadas      índices bloqueados o vendidos (Reservas)
 * @param {number[]} seleccionadas índices elegidos
 * @param {function} onToggle      (idx) => void
 */
export default function MapaButacas({ layout, ocupadas, seleccionadas, onToggle, etiquetaElegida = 'Seleccionada' }) {
  const { filas, cols } = layout;
  const filasArr = Array.from({ length: filas }, (_, f) => f);
  const colsArr = Array.from({ length: cols }, (_, c) => c);

  return (
    <>
      <div className="compra-pantalla">
        <div className="compra-pantalla-barra" />
        <span>PANTALLA DE PROYECCIÓN</span>
      </div>

      <div className="compra-leyenda">
        <span className="compra-leyenda-item"><span className="butaca butaca--libre" /> Libre</span>
        <span className="compra-leyenda-item"><span className="butaca butaca--ocupada" /> Ocupada</span>
        <span className="compra-leyenda-item"><span className="butaca butaca--elegida" /> {etiquetaElegida}</span>
      </div>

      <div className="compra-mapa" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <div className="compra-mapa-fila">
          <span className="compra-fila-letra" />
          {colsArr.map((c) => (
            <span key={c} className="compra-col-num">{c + 1}</span>
          ))}
        </div>

        {filasArr.map((f) => (
          <div key={f} className="compra-mapa-fila">
            <span className="compra-fila-letra">{String.fromCharCode(65 + f)}</span>
            {colsArr.map((c) => {
              const idx = f * cols + c;
              const ocupada = ocupadas.has(idx);
              const elegida = seleccionadas.includes(idx);
              const nombre = `Fila ${String.fromCharCode(65 + f)}, butaca ${c + 1}`;
              return (
                <button
                  key={idx}
                  type="button"
                  className={`butaca${ocupada ? ' butaca--ocupada' : elegida ? ' butaca--elegida' : ' butaca--libre'}`}
                  onClick={() => onToggle(idx)}
                  disabled={ocupada}
                  title={`${nombre}${ocupada ? ' (ocupada)' : ''}`}
                  aria-label={nombre}
                  aria-pressed={elegida}
                />
              );
            })}
          </div>
        ))}
      </div>
    </>
  );
}
