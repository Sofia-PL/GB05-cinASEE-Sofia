import { useState } from 'react';
import { useAnchura, techoEje } from './useAnchura';

/* =========================================================
   Barras horizontales de una sola serie (magnitud por categoría)
   - Barras de 20px con extremo de dato redondeado (4px) y base recta.
   - Valor escrito al final de cada barra, con el color del texto.
   - Tooltip al pasar el ratón o al enfocar con el teclado.
   ========================================================= */

const ALTO_BARRA = 20;
const SEPARACION = 14;
const RADIO = 4;

/** Rectángulo con solo el extremo derecho redondeado */
function rutaBarra(x, y, w, h) {
  const r = Math.min(RADIO, w / 2, h / 2);
  if (w <= 0) return '';
  return `M${x},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h - r} Q${x + w},${y + h} ${x + w - r},${y + h} H${x} Z`;
}

/**
 * @param {{ id: string, etiqueta: string, valor: number, detalle?: string[] }[]} datos
 * @param {(v: number) => string} formato  texto del valor
 * @param {number} [maximo]  final del eje (p. ej. 100 para porcentajes)
 */
export default function BarrasHorizontales({ datos, formato, maximo, titulo }) {
  const [ref, anchura] = useAnchura();
  const [activa, setActiva] = useState(null);

  if (datos.length === 0) {
    return <div ref={ref} className="viz-root viz-vacio">Sin datos para mostrar.</div>;
  }

  const anchoEtiquetas = Math.min(200, Math.max(90, anchura * 0.3));
  const anchoValor = 80;
  const x0 = anchoEtiquetas + 8;
  const anchoPlot = Math.max(60, anchura - x0 - anchoValor);
  const fin = maximo ?? techoEje(Math.max(...datos.map((d) => d.valor)));
  const escala = (v) => (Math.max(0, v) / fin) * anchoPlot;
  const alto = datos.length * (ALTO_BARRA + SEPARACION) + 24;
  const marcas = [0, 0.25, 0.5, 0.75, 1].map((t) => t * fin);
  const d = activa !== null ? datos[activa] : null;
  const yActiva = activa !== null ? activa * (ALTO_BARRA + SEPARACION) : 0;

  return (
    <div ref={ref} className="viz-root">
      <svg width={anchura} height={alto} role="img" aria-label={titulo}>
        {/* Cuadrícula vertical discreta */}
        {marcas.map((m) => (
          <g key={m}>
            <line x1={x0 + escala(m)} x2={x0 + escala(m)} y1={0} y2={alto - 20} stroke="var(--viz-grid)" />
            <text className="viz-eje" x={x0 + escala(m)} y={alto - 6} textAnchor="middle">{formato(m)}</text>
          </g>
        ))}
        <line x1={x0} x2={x0} y1={0} y2={alto - 20} stroke="var(--viz-axis)" />

        {datos.map((dato, i) => {
          const y = i * (ALTO_BARRA + SEPARACION) + SEPARACION / 2;
          const w = escala(dato.valor);
          return (
            <g
              key={dato.id}
              className="viz-grupo"
              tabIndex={0}
              aria-label={`${dato.etiqueta}: ${formato(dato.valor)}`}
              onMouseEnter={() => setActiva(i)}
              onMouseLeave={() => setActiva(null)}
              onFocus={() => setActiva(i)}
              onBlur={() => setActiva(null)}
            >
              {/* Zona de interacción más grande que la barra */}
              <rect className="viz-hit" x={0} y={y - SEPARACION / 2} width={anchura} height={ALTO_BARRA + SEPARACION} />
              <text className="viz-etiqueta" x={anchoEtiquetas} y={y + ALTO_BARRA / 2} dy="0.35em" textAnchor="end">
                {dato.etiqueta.length > 26 ? `${dato.etiqueta.slice(0, 25)}…` : dato.etiqueta}
              </text>
              <path className="viz-marca" d={rutaBarra(x0, y, w, ALTO_BARRA)} />
              <text className="viz-etiqueta" x={x0 + w + 6} y={y + ALTO_BARRA / 2} dy="0.35em" fontWeight="600">
                {formato(dato.valor)}
              </text>
            </g>
          );
        })}
      </svg>

      {d && (
        <div className="viz-tooltip" style={{ left: x0 + Math.min(escala(d.valor), anchoPlot) / 2, top: yActiva + SEPARACION / 2 }}>
          <strong>{d.etiqueta}</strong>
          <span className="viz-tooltip-muestra" />{formato(d.valor)}
          {(d.detalle || []).map((linea) => <div key={linea}>{linea}</div>)}
        </div>
      )}
    </div>
  );
}
