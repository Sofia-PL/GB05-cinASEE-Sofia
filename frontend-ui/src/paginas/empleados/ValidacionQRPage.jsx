import { useState } from 'react';
import { useAuth } from '../../context/useAuth';
import { useCine } from '../../context/useCine';
import { validarEntrada, nombreButaca } from '../../api/reservas';
import { obtenerSala, obtenerSesion, obtenerPelicula } from '../../api/cartelera';

/*
 * Validación de entradas por código. La lectura del QR la hace un dispositivo
 * asignado a cada empleado, fuera de esta aplicación: aquí solo se introduce el código.
 */

/** Título de la película y nombre de la sala de la entrada validada (si Cartelera responde) */
async function detallesEntrada(c, token) {
  const [sala, pelicula] = await Promise.all([
    obtenerSala(c.roomId, token).catch(() => null),
    obtenerSesion(c.showId, token)
      .then((s) => obtenerPelicula(s.movieId))
      .catch(() => null),
  ]);
  return { sala, pelicula };
}

export default function ValidacionQRPage() {
  const { tokenUsuario } = useAuth();
  const { cines } = useCine();
  const token = tokenUsuario();
  const [codigo, setCodigo] = useState('');
  const [resultado, setResultado] = useState(null);
  const [validando, setValidando] = useState(false);

  /** La entrada se valida en Reservas (Comp. 2), que la marca como usada */
  async function validar(codigoLeido) {
    const cleanCode = (codigoLeido || '').trim();
    if (!cleanCode) {
      setResultado({ valido: false, mensaje: 'Introduce un código de entrada antes de validar.' });
      return;
    }
    setValidando(true);
    setResultado(null);
    try {
      const c = await validarEntrada(cleanCode, token);
      const { sala, pelicula } = await detallesEntrada(c, token);
      const inicio = new Date(c.showStartTime);
      setResultado({
        valido: true,
        mensaje: 'Entrada verificada y marcada como utilizada. Acceso permitido a la sala.',
        ticket: {
          codigo: c.ticketCode,
          reserva: c.reservationId,
          pelicula: pelicula?.title || 'Película',
          cine: cines.find((x) => x.id === sala?.cinemaId)?.name || '',
          sala: sala?.name || 'Sala',
          hora: `${inicio.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' })}, ${inicio.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })}`,
          butaca: nombreButaca(c.row, c.column),
        },
      });
      setCodigo('');
    } catch (err) {
      const mensajes = {
        404: 'Código no registrado en el sistema.',
      };
      const porCodigo = {
        TICKET_ALREADY_USED: 'Esta entrada ya se utilizó.',
        TICKET_NOT_VALID: 'La entrada no es válida (reserva no pagada, cancelada o devuelta).',
      };
      setResultado({ valido: false, mensaje: porCodigo[err.code] || mensajes[err.status] || err.message });
    } finally {
      setValidando(false);
    }
  }

  function handleSubmit(e) {
    e.preventDefault();
    validar(codigo);
  }

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto', padding: 'clamp(28px, 4vw, 56px) var(--gutter)' }}>
      <header style={{ marginBottom: '28px', textAlign: 'center' }}>
        <p className="home-eyebrow">Terminal de Staff</p>
        <h1 style={{ color: 'var(--burgundy)', fontSize: '2.4rem', margin: '6px 0' }}>
          Validación de Entradas
        </h1>
        <p style={{ color: 'var(--text-muted)' }}>
          Introduce el código de la entrada para dar acceso a sala.
        </p>
      </header>

      <div style={{
        background: 'white',
        borderRadius: 'var(--radius-lg)',
        padding: '32px',
        border: '1px solid rgba(217, 164, 65, 0.3)',
        boxShadow: 'var(--shadow-md)',
        marginBottom: '24px',
      }}>
        {!token && (
          <div className="cinasee-error" role="alert">Tu sesión ha caducado. Vuelve a iniciar sesión para validar entradas.</div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <input
            type="text"
            placeholder="Código de la entrada (QR-…)"
            value={codigo}
            onChange={(e) => setCodigo(e.target.value)}
            aria-label="Código de la entrada"
            style={{
              flex: 1,
              minWidth: '220px',
              padding: '12px 16px',
              borderRadius: 'var(--radius-sm)',
              border: '1px solid rgba(43, 18, 21, 0.25)',
              fontSize: '1rem',
              fontFamily: 'monospace',
              letterSpacing: '0.05em',
            }}
          />
          <button className="home-button" type="submit" disabled={validando || !token} style={{ padding: '0.8em 1.6em' }}>
            {validando ? 'Validando…' : 'Validar código'}
          </button>
        </form>

        {resultado && (
          <div style={{
            marginTop: '24px',
            padding: '20px',
            borderRadius: 'var(--radius-sm)',
            background: resultado.valido ? 'rgba(40, 167, 69, 0.1)' : 'rgba(220, 53, 69, 0.1)',
            border: `1px solid ${resultado.valido ? 'rgba(40, 167, 69, 0.4)' : 'rgba(220, 53, 69, 0.4)'}`,
          }}>
            <h3 style={{ margin: '0 0 8px', color: resultado.valido ? '#155724' : '#721c24' }}>
              {resultado.valido ? 'Acceso autorizado' : 'Acceso denegado'}
            </h3>
            <p style={{ margin: '0 0 12px', color: 'var(--dark-burgundy)' }}>{resultado.mensaje}</p>

            {resultado.valido && resultado.ticket && (
              <div style={{ background: 'white', padding: '16px', borderRadius: '6px', border: '1px solid rgba(0,0,0,0.08)' }}>
                <p style={{ margin: '0 0 4px' }}><strong>Película:</strong> {resultado.ticket.pelicula}</p>
                <p style={{ margin: '0 0 4px' }}>
                  <strong>Sala:</strong> {[resultado.ticket.cine, resultado.ticket.sala].filter(Boolean).join(', ')}
                </p>
                <p style={{ margin: '0 0 4px' }}><strong>Sesión:</strong> {resultado.ticket.hora}</p>
                <p style={{ margin: '0 0 4px' }}><strong>Butaca:</strong> {resultado.ticket.butaca}</p>
                <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                  Reserva nº {resultado.ticket.reserva}, código <code>{resultado.ticket.codigo}</code>
                </p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
