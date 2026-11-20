import { useState, useEffect, useRef } from 'react';
import { enmascararHora, horaValida } from '../utils/fechas';

/* =========================================================
   Campo de hora en formato de 24 h (hh:mm)
   ---------------------------------------------------------
   Sustituye a <input type="time">, que en un navegador en inglés
   muestra la hora con AM/PM. El valor que entra y sale es "HH:MM".
   ========================================================= */

/**
 * @param {string}   value     hora en HH:MM
 * @param {function} onChange  (hhmm) => void; solo se llama con horas válidas
 */
export default function CampoHora({
  id, value, onChange, required = false, disabled = false,
  className = 'admin-input', style, 'aria-label': ariaLabel,
}) {
  const [texto, setTexto] = useState(value || '');
  const [valorPrevio, setValorPrevio] = useState(value);
  const input = useRef(null);

  // Si la hora cambia desde fuera, se actualiza el texto
  if (value !== valorPrevio) {
    setValorPrevio(value);
    setTexto(value || '');
  }

  const invalido = texto !== '' && !horaValida(texto);

  // El formulario no se envía con una hora mal escrita
  useEffect(() => {
    input.current?.setCustomValidity(invalido ? 'Escribe la hora como hh:mm (de 00:00 a 23:59).' : '');
  }, [invalido]);

  function escribir(e) {
    const nuevo = enmascararHora(e.target.value);
    setTexto(nuevo);
    if (horaValida(nuevo)) {
      setValorPrevio(nuevo);
      onChange(nuevo);
    }
  }

  return (
    <input
      ref={input}
      id={id}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      placeholder="hh:mm"
      className={className}
      value={texto}
      onChange={escribir}
      required={required}
      disabled={disabled}
      aria-label={ariaLabel}
      aria-invalid={invalido}
      title={invalido ? 'Escribe la hora como hh:mm (de 00:00 a 23:59).' : undefined}
      style={{ ...style, ...(invalido ? { borderColor: '#d9383a', background: '#fff5f5' } : {}) }}
    />
  );
}
