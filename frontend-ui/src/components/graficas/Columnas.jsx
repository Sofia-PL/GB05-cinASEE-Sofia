import { useState } from 'react';
import { useAnchura, techoEje } from './useAnchura';

/* =========================================================
   Columnas de una sola serie a lo largo del tiempo (p. ej. ingresos por día)
   - Columnas de hasta 24px con la parte superior redondeada (4px).
   - Solo se rotula el valor máximo; el resto, en el tooltip.
   ========================================================= */

const ALTO = 220;
const MARGEN = { arriba: 22, abajo: 28, izq: 56, der: 8 };
const RADIO = 4;

/** Rectángulo con solo el extremo superior redondeado */
function rutaColumna(x, yBase, w, h) {
  if (h <= 0) return '';
  const r = Math.min(RADIO, w / 2, h);
  const top = yBase - h;
  return `M${x},${yBase} V${top + r} Q${x},${top} ${x + r},${top} H${x + w - r} Q${x + w},${top} ${x + w},${top + r} V${yBase} Z`;
}

/**
 * @param {{ id: string, etiqueta: string, etiquetaLarga?: string, valor: number, detalle?: string[] }[]} datos
 * @param {(v: number) => string} formato
 */
export default function Columnas({ datos, formato, titulo }) {
  const [ref, anchura] = useAnchura();
  const [activa, setActiva] = useState(null);

  if (datos.length === 0) {
    return <div ref={ref} className="viz-root viz-vacio">Sin datos para mostrar.</div>;
  }

  const anchoPlot = anchura - MARGEN.izq - MARGEN.der;
  const altoPlot = ALTO - MARGEN.arriba - MARGEN.abajo;
  const banda = anchoPlot / datos.length;
  const anchoCol = Math.max(2, Math.min(24, banda - 2));   // 2px de aire mínimo entre columnas
  const fin = techoEje(Math.max(...datos.map((d) => d.valor)));
  const escala = (v) => (Math.max(0, v) / fin) * altoPlot;
  const yBase = MARGEN.arriba + altoPlot;
  const marcas = [0, 0.5, 1].map((t) => t * fin);
  const iMax = datos.reduce((m, d, i) => (d.valor > datos[m].valor ? i : m), 0);
  // Cada cuántas columnas se escribe la fecha para que no se pisen
  const cadaCuantas = Math.max(1, Math.ceil(46 / banda));
  const d = activa !== null ? datos[activa] : null;

  return (
    <div ref={ref} className="viz-root">
      <svg width={anchura} height={ALTO} role="img" aria-label={titulo}>
        {marcas.map((m) => (
          <g key={m}>
            <line x1={MARGEN.izq} x2={anchura - MARGEN.der} y1={yBase - escala(m)} y2={yBase - escala(m)} stroke={m === 0 ? 'var(--viz-axis)' : 'var(--viz-grid)'} />
            <text className="viz-eje" x={MARGEN.izq - 6} y={yBase - escala(m)} dy="0.35em" textAnchor="end">{formato(m)}</text>
          </g>
        ))}

        {datos.map((dato, i) => {
          const xBanda = MARGEN.izq + i * banda;
          const x = xBanda + (banda - anchoCol) / 2;
          const h = escala(dato.valor);
          return (
            <g
              key={dato.id}
              className="viz-grupo"
              tabIndex={0}
              aria-label={`${dato.etiquetaLarga || dato.etiqueta}: ${formato(dato.valor)}`}
              onMouseEnter={() => setActiva(i)}
              onMouseLeave={() => setActiva(null)}
              onFocus={() => setActiva(i)}
              onBlur={() => setActiva(null)}
            >
              <rect className="viz-hit" x={xBanda} y={MARGEN.arriba} width={banda} height={altoPlot} />
              <path className="viz-marca" d={rutaColumna(x, yBase, anchoCol, h)} />
              {i === iMax && dato.valor > 0 && (
                <text className="viz-etiqueta" x={x + anchoCol / 2} y={yBase - h - 6} textAnchor="middle" fontWeight="600">
                  {formato(dato.valor)}
                </text>
              )}
              {i % cadaCuantas === 0 && (
                <text className="viz-eje" x={xBanda + banda / 2} y={ALTO - 8} textAnchor="middle">{dato.etiqueta}</text>
              )}
            </g>
          );
        })}
      </svg>

      {d && (
        <div
          className="viz-tooltip"
          style={{ left: MARGEN.izq + activa * banda + banda / 2, top: yBase - escala(d.valor) }}
        >
          <strong>{d.etiquetaLarga || d.etiqueta}</strong>
          <span className="viz-tooltip-muestra" />{formato(d.valor)}
          {(d.detalle || []).map((linea) => <div key={linea}>{linea}</div>)}
        </div>
      )}
    </div>
  );
}
