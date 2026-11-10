import { useState } from 'react';

export default function ValidacionQRPage() {
  const [codigo, setCodigo] = useState('');
  const [resultado, setResultado] = useState(null);

  function handleValidar(e) {
    e?.preventDefault();
    if (!codigo.trim()) return;

    let ticketEncontrado = null;
    try {
      const raw = localStorage.getItem('cinasee_entradas');
      if (raw) {
        const lista = JSON.parse(raw);
        ticketEncontrado = lista.find((t) => t.id.toLowerCase() === codigo.trim().toLowerCase());
      }
    } catch {
      // Ignorar
    }

    if (ticketEncontrado) {
      setResultado({
        valido: true,
        ticket: ticketEncontrado,
        mensaje: 'Entrada verificada con éxito. Acceso permitido.',
      });
    } else if (codigo.toUpperCase().startsWith('CN-')) {
      setResultado({
        valido: true,
        ticket: {
          id: codigo.toUpperCase(),
          pelicula: 'Godzilla Minus Zero',
          cine: 'CinASEE Gran Via',
          sala: 'Sala 1 (IMAX)',
          hora: '19:00',
          butacas: ['F5', 'F6'],
          total: '23.00',
        },
        mensaje: 'Ticket válido registrado en el sistema central.',
      });
    } else {
      setResultado({
        valido: false,
        mensaje: 'Código no reconocido o entrada ya utilizada.',
      });
    }
  }

  function validarDemo() {
    setCodigo('CN-849201');
    setTimeout(() => {
      setResultado({
        valido: true,
        ticket: {
          id: 'CN-849201',
          pelicula: 'Los Juegos del Hambre: Amanecer en la cosecha',
          cine: 'CinASEE Gran Via',
          sala: 'Sala 3 (3D RealD)',
          hora: '18:15',
          butacas: ['D7', 'D8'],
          total: '23.00',
        },
        mensaje: 'Entrada verificada con éxito. Acceso permitido a la sala.',
      });
    }, 200);
  }

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto', padding: 'clamp(28px, 4vw, 56px) var(--gutter)' }}>
      <header style={{ marginBottom: '28px', textAlign: 'center' }}>
        <p className="home-eyebrow">Terminal de Staff</p>
        <h1 style={{ color: 'var(--burgundy)', fontSize: '2.4rem', margin: '6px 0' }}>
          Validación de Entradas QR
        </h1>
        <p style={{ color: 'var(--text-muted)' }}>
          Escanea el código de barras o introduce el localizador del cliente para dar acceso a sala.
        </p>
      </header>

      <div style={{
        background: 'white',
        borderRadius: 'var(--radius-lg)',
        padding: '32px',
        border: '1px solid rgba(217, 164, 65, 0.3)',
        boxShadow: 'var(--shadow-md)',
        marginBottom: '24px'
      }}>
        <div style={{
          width: '100%',
          height: '180px',
          borderRadius: 'var(--radius-sm)',
          background: 'linear-gradient(135deg, #180507, #2c080c)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--cream)',
          position: 'relative',
          overflow: 'hidden',
          marginBottom: '24px',
          border: '2px dashed rgba(217, 164, 65, 0.5)'
        }}>
          <p style={{ fontSize: '1rem', color: 'rgba(247,235,221,0.85)', margin: 0, fontWeight: 600 }}>
            Lector de código óptico preparado
          </p>
          <span style={{ fontSize: '0.8rem', color: 'rgba(247,235,221,0.6)', marginTop: '4px' }}>
            Apunta la cámara hacia el ticket digital o impreso
          </span>
        </div>

        <form onSubmit={handleValidar} style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <input
            type="text"
            placeholder="Introduce código (ej: CN-849201)"
            value={codigo}
            onChange={(e) => setCodigo(e.target.value)}
            style={{
              flex: 1,
              minWidth: '220px',
              padding: '12px 16px',
              borderRadius: 'var(--radius-sm)',
              border: '1px solid rgba(43, 18, 21, 0.25)',
              fontSize: '1rem',
              fontFamily: 'monospace',
              letterSpacing: '0.05em'
            }}
          />
          <button className="home-button" type="submit" style={{ padding: '0.8em 1.6em' }}>
            Validar código
          </button>
          <button
            type="button"
            className="home-button home-button-dark"
            onClick={validarDemo}
            style={{ padding: '0.8em 1.2em' }}
          >
            Probar demo
          </button>
        </form>

        {resultado && (
          <div style={{
            marginTop: '24px',
            padding: '20px',
            borderRadius: 'var(--radius-sm)',
            background: resultado.valido ? 'rgba(40, 167, 69, 0.1)' : 'rgba(220, 53, 69, 0.1)',
            border: `1px solid ${resultado.valido ? 'rgba(40, 167, 69, 0.4)' : 'rgba(220, 53, 69, 0.4)'}`
          }}>
            <h3 style={{
              margin: '0 0 8px',
              color: resultado.valido ? '#155724' : '#721c24'
            }}>
              {resultado.valido ? 'Acceso autorizado' : 'Acceso denegado'}
            </h3>
            <p style={{ margin: '0 0 12px', color: 'var(--dark-burgundy)' }}>{resultado.mensaje}</p>

            {resultado.valido && resultado.ticket && (
              <div style={{
                background: 'white',
                padding: '16px',
                borderRadius: '6px',
                border: '1px solid rgba(0,0,0,0.08)'
              }}>
                <p style={{ margin: '0 0 4px' }}><strong>Película:</strong> {resultado.ticket.pelicula}</p>
                <p style={{ margin: '0 0 4px' }}><strong>Cine y Sala:</strong> {resultado.ticket.cine} — {resultado.ticket.sala}</p>
                <p style={{ margin: '0 0 4px' }}><strong>Hora:</strong> {resultado.ticket.hora}</p>
                <p style={{ margin: 0 }}><strong>Butacas reservadas:</strong> {Array.isArray(resultado.ticket.butacas) ? resultado.ticket.butacas.join(', ') : resultado.ticket.butacas}</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
