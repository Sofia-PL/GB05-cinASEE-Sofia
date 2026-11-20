import { useState, useEffect, useRef } from 'react';
import { fechaEs, isoDesdeFechaEs, enmascararFecha } from '../utils/fechas';

/* =========================================================
   Campo de fecha en formato español (dd/mm/aaaa)
   ---------------------------------------------------------
   Sustituye a <input type="date">, que muestra el formato del
   idioma del navegador (MM/DD/YYYY en inglés). Se puede escribir
   la fecha o elegirla en el calendario. El valor que entra y sale
   es siempre AAAA-MM-DD, como antes.
   ========================================================= */

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
const DIAS = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];

function isoDe(a, m, d) {
  return `${a}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

/** Mes que muestra el calendario: el de la fecha elegida o el actual */
function mesDe(iso) {
  const [a, m] = String(iso || '').split('-').map(Number);
  if (a && m) return { a, m: m - 1 };
  const hoy = new Date();
  return { a: hoy.getFullYear(), m: hoy.getMonth() };
}

/**
 * @param {string}   value     fecha en AAAA-MM-DD ('' si vacía)
 * @param {function} onChange  (iso) => void; solo se llama con fechas válidas y dentro de [min, max]
 * @param {string}   [min]     AAAA-MM-DD
 * @param {string}   [max]     AAAA-MM-DD
 */
export default function CampoFecha({
  id, value, onChange, min, max, required = false, disabled = false,
  className = 'admin-input', style, 'aria-label': ariaLabel,
}) {
  const [texto, setTexto] = useState(fechaEs(value));
  const [valorPrevio, setValorPrevio] = useState(value);
  const [abierto, setAbierto] = useState(false);
  const [mes, setMes] = useState(() => mesDe(value));
  const contenedor = useRef(null);
  const input = useRef(null);

  // Si la fecha cambia desde fuera (otro campo, reinicio del formulario...), se actualiza el texto
  if (value !== valorPrevio) {
    setValorPrevio(value);
    setTexto(fechaEs(value));
  }

  const isoEscrito = isoDesdeFechaEs(texto);
  const fueraDeRango = Boolean(isoEscrito) && ((min && isoEscrito < min) || (max && isoEscrito > max));
  const invalido = texto !== '' && (!isoEscrito || fueraDeRango);
  const mensajeError = !invalido
    ? ''
    : !isoEscrito
      ? 'Escribe la fecha como dd/mm/aaaa.'
      : min && isoEscrito < min
        ? `La fecha no puede ser anterior al ${fechaEs(min)}.`
        : `La fecha no puede ser posterior al ${fechaEs(max)}.`;

  // El formulario no se envía con una fecha mal escrita (validación nativa del navegador)
  useEffect(() => {
    input.current?.setCustomValidity(mensajeError);
  }, [mensajeError]);

  // El calendario se cierra al pulsar fuera o con Escape
  useEffect(() => {
    if (!abierto) return undefined;
    function alPulsarFuera(e) {
      if (!contenedor.current?.contains(e.target)) setAbierto(false);
    }
    function alPulsarTecla(e) {
      if (e.key === 'Escape') setAbierto(false);
    }
    document.addEventListener('mousedown', alPulsarFuera);
    document.addEventListener('keydown', alPulsarTecla);
    return () => {
      document.removeEventListener('mousedown', alPulsarFuera);
      document.removeEventListener('keydown', alPulsarTecla);
    };
  }, [abierto]);

  function escribir(e) {
    const nuevo = enmascararFecha(e.target.value);
    setTexto(nuevo);
    const iso = isoDesdeFechaEs(nuevo);
    if (iso && !(min && iso < min) && !(max && iso > max)) {
      setValorPrevio(iso);
      onChange(iso);
    } else if (nuevo === '' && !required) {
      setValorPrevio('');
      onChange('');
    }
  }

  function elegir(iso) {
    setTexto(fechaEs(iso));
    setValorPrevio(iso);
    onChange(iso);
    setAbierto(false);
    input.current?.focus();
  }

  function abrirCalendario() {
    setMes(mesDe(isoEscrito || value || min));
    setAbierto((a) => !a);
  }

  function moverMes(delta) {
    setMes(({ a, m }) => {
      const d = new Date(a, m + delta, 1);
      return { a: d.getFullYear(), m: d.getMonth() };
    });
  }

  // Huecos antes del día 1 (la semana empieza en lunes) y días del mes
  const primerDia = (new Date(mes.a, mes.m, 1).getDay() + 6) % 7;
  const diasMes = new Date(mes.a, mes.m + 1, 0).getDate();
  const celdas = [...Array(primerDia).fill(null), ...Array.from({ length: diasMes }, (_, i) => i + 1)];
  const hoyIso = isoDe(new Date().getFullYear(), new Date().getMonth(), new Date().getDate());

  return (
    <div ref={contenedor} style={{ position: 'relative', display: 'flex', gap: '6px', alignItems: 'center', ...style }}>
      <input
        ref={input}
        id={id}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        placeholder="dd/mm/aaaa"
        className={className}
        value={texto}
        onChange={escribir}
        required={required}
        disabled={disabled}
        aria-label={ariaLabel}
        aria-invalid={invalido}
        title={mensajeError || undefined}
        style={{ flex: 1, minWidth: 0, ...(invalido ? { borderColor: '#d9383a', background: '#fff5f5' } : {}) }}
      />
      <button
        type="button"
        className="admin-btn admin-btn-secondary admin-btn-sm"
        onClick={abrirCalendario}
        disabled={disabled}
        aria-label="Abrir calendario"
        aria-expanded={abierto}
        style={{ flexShrink: 0, padding: '6px 8px', lineHeight: 0 }}
      >
        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true">
          <path d="M19 4h-1V2h-2v2H8V2H6v2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2zm0 16H5V10h14v10zm0-12H5V6h14v2z" />
        </svg>
      </button>

      {abierto && (
        <div
          role="dialog"
          aria-label="Calendario"
          style={{
            position: 'absolute', top: 'calc(100% + 4px)', left: 0, zIndex: 50,
            background: '#fff', border: '1px solid rgba(43,18,21,0.15)', borderRadius: '6px',
            boxShadow: '0 8px 24px rgba(43,18,21,0.15)', padding: '10px', width: '252px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
            <button type="button" className="admin-btn admin-btn-secondary admin-btn-sm" onClick={() => moverMes(-1)} aria-label="Mes anterior">‹</button>
            <strong style={{ textTransform: 'capitalize', color: 'var(--burgundy)', fontSize: '0.92rem' }}>
              {MESES[mes.m]} {mes.a}
            </strong>
            <button type="button" className="admin-btn admin-btn-secondary admin-btn-sm" onClick={() => moverMes(1)} aria-label="Mes siguiente">›</button>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: '2px', textAlign: 'center' }}>
            {DIAS.map((d) => (
              <span key={d} style={{ fontSize: '0.72rem', fontWeight: 700, color: 'var(--text-muted)', padding: '4px 0' }}>{d}</span>
            ))}
            {celdas.map((dia, i) => {
              if (dia === null) return <span key={`hueco-${i}`} />;
              const iso = isoDe(mes.a, mes.m, dia);
              const deshabilitado = (min && iso < min) || (max && iso > max);
              const elegido = iso === value;
              return (
                <button
                  key={iso}
                  type="button"
                  onClick={() => elegir(iso)}
                  disabled={deshabilitado}
                  aria-pressed={elegido}
                  aria-label={`${dia} de ${MESES[mes.m]} de ${mes.a}`}
                  style={{
                    border: iso === hoyIso ? '1px solid var(--gold)' : '1px solid transparent',
                    borderRadius: '4px',
                    padding: '5px 0',
                    fontSize: '0.84rem',
                    cursor: deshabilitado ? 'not-allowed' : 'pointer',
                    background: elegido ? 'var(--burgundy)' : 'transparent',
                    color: elegido ? '#fff' : deshabilitado ? 'rgba(43,18,21,0.3)' : 'var(--dark-burgundy)',
                    fontWeight: elegido ? 700 : 400,
                  }}
                >
                  {dia}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
