import { useState } from 'react';
import peliculas from '../../assets/peliculas.json';
import cines from '../../assets/cines.json';

export default function TaquillaPage() {
  const [cineId, setCineId] = useState(cines[0].id);
  const [peliculaId, setPeliculaId] = useState(peliculas[0].id);
  const [hora, setHora] = useState('18:30');
  const [entradas, setEntradas] = useState(2);
  const [medioPago, setMedioPago] = useState('tarjeta');
  const [ticketEmitido, setTicketEmitido] = useState(null);

  const cineSel = cines.find((c) => c.id === Number(cineId)) || cines[0];
  const peliSel = peliculas.find((p) => p.id === Number(peliculaId)) || peliculas[0];
  const precioUnitario = 9.50;
  const total = (precioUnitario * entradas).toFixed(2);

  function handleVender(e) {
    e.preventDefault();
    const id = `TPV-${Math.floor(100000 + Math.random() * 900000)}`;
    setTicketEmitido({
      id,
      cine: cineSel.name,
      pelicula: peliSel.title,
      hora,
      entradas,
      total,
      medioPago,
      horaEmision: new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })
    });
  }

  return (
    <div style={{ maxWidth: '900px', margin: '0 auto', padding: 'clamp(28px, 4vw, 56px) var(--gutter)' }}>
      <header style={{ marginBottom: '28px', textAlign: 'center' }}>
        <p className="home-eyebrow">Terminal TPV Staff</p>
        <h1 style={{ color: 'var(--burgundy)', fontSize: '2.4rem', margin: '6px 0' }}>
          Venta en Taquilla Presencial
        </h1>
        <p style={{ color: 'var(--text-muted)' }}>
          Expedición presencial de tickets y cobro directo en mostrador.
        </p>
      </header>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
        gap: '24px'
      }}>
        <form onSubmit={handleVender} style={{
          background: 'white',
          borderRadius: 'var(--radius-lg)',
          padding: '28px',
          border: '1px solid rgba(217, 164, 65, 0.3)',
          boxShadow: 'var(--shadow-md)',
          display: 'flex',
          flexDirection: 'column',
          gap: '16px'
        }}>
          <div>
            <label style={{ display: 'block', fontWeight: 600, fontSize: '0.9rem', marginBottom: '6px' }}>
              Cine:
            </label>
            <select
              value={cineId}
              onChange={(e) => setCineId(e.target.value)}
              style={{ width: '100%', padding: '10px 14px', borderRadius: 'var(--radius-sm)', border: '1px solid rgba(43,18,21,0.2)', font: 'inherit' }}
            >
              {cines.map((c) => (
                <option key={c.id} value={c.id}>{c.name} ({c.city})</option>
              ))}
            </select>
          </div>

          <div>
            <label style={{ display: 'block', fontWeight: 600, fontSize: '0.9rem', marginBottom: '6px' }}>
              Película:
            </label>
            <select
              value={peliculaId}
              onChange={(e) => setPeliculaId(e.target.value)}
              style={{ width: '100%', padding: '10px 14px', borderRadius: 'var(--radius-sm)', border: '1px solid rgba(43,18,21,0.2)', font: 'inherit' }}
            >
              {peliculas.map((p) => (
                <option key={p.id} value={p.id}>{p.title} ({p.genre})</option>
              ))}
            </select>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div>
              <label style={{ display: 'block', fontWeight: 600, fontSize: '0.9rem', marginBottom: '6px' }}>
                Sesión:
              </label>
              <select
                value={hora}
                onChange={(e) => setHora(e.target.value)}
                style={{ width: '100%', padding: '10px 14px', borderRadius: 'var(--radius-sm)', border: '1px solid rgba(43,18,21,0.2)', font: 'inherit' }}
              >
                <option value="16:00">16:00 (Sala 1)</option>
                <option value="18:30">18:30 (Sala 2)</option>
                <option value="20:45">20:45 (Sala 3)</option>
                <option value="22:30">22:30 (Sala 5)</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontWeight: 600, fontSize: '0.9rem', marginBottom: '6px' }}>
                Entradas:
              </label>
              <input
                type="number"
                min="1"
                max="10"
                value={entradas}
                onChange={(e) => setEntradas(Math.max(1, parseInt(e.target.value, 10) || 1))}
                style={{ width: '100%', padding: '10px 14px', borderRadius: 'var(--radius-sm)', border: '1px solid rgba(43,18,21,0.2)', font: 'inherit' }}
              />
            </div>
          </div>

          <div>
            <label style={{ display: 'block', fontWeight: 600, fontSize: '0.9rem', marginBottom: '6px' }}>
              Forma de cobro:
            </label>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button
                type="button"
                className={`login-tab${medioPago === 'tarjeta' ? ' active' : ''}`}
                onClick={() => setMedioPago('tarjeta')}
                style={{ flex: 1, padding: '10px', borderRadius: 'var(--radius-sm)', border: '1px solid rgba(43,18,21,0.2)' }}
              >
                Datáfono / Tarjeta
              </button>
              <button
                type="button"
                className={`login-tab${medioPago === 'efectivo' ? ' active' : ''}`}
                onClick={() => setMedioPago('efectivo')}
                style={{ flex: 1, padding: '10px', borderRadius: 'var(--radius-sm)', border: '1px solid rgba(43,18,21,0.2)' }}
              >
                Efectivo
              </button>
            </div>
          </div>

          <div style={{
            background: 'rgba(217,164,65,0.15)',
            padding: '12px 16px',
            borderRadius: 'var(--radius-sm)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: '8px'
          }}>
            <span style={{ fontWeight: 600, color: 'var(--dark-burgundy)' }}>Total a cobrar:</span>
            <strong style={{ fontSize: '1.4rem', color: 'var(--granate)' }}>{total} €</strong>
          </div>

          <button className="home-button" type="submit" style={{ justifyContent: 'center', marginTop: '8px' }}>
            Cobrar e imprimir ticket
          </button>
        </form>

        <div style={{
          background: 'white',
          borderRadius: 'var(--radius-lg)',
          padding: '28px',
          border: '1px dashed rgba(43, 18, 21, 0.3)',
          boxShadow: 'var(--shadow-md)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: ticketEmitido ? 'start' : 'center',
          textAlign: 'center'
        }}>
          {ticketEmitido ? (
            <div style={{
              width: '100%',
              maxWidth: '320px',
              border: '2px solid #2c080c',
              borderRadius: '8px',
              padding: '20px',
              fontFamily: 'monospace',
              background: '#fdfbfa',
              textAlign: 'left'
            }}>
              <p style={{ textAlign: 'center', fontWeight: 'bold', fontSize: '1.1rem', margin: '0 0 4px' }}>
                CINASEE TICKETS
              </p>
              <p style={{ textAlign: 'center', fontSize: '0.8rem', color: 'gray', margin: '0 0 12px' }}>
                COMPROBANTE DE COMPRA
              </p>
              <hr style={{ border: 'none', borderTop: '1px dashed #2c080c', margin: '10px 0' }} />
              <p><strong>CINE:</strong> {ticketEmitido.cine}</p>
              <p><strong>PELÍCULA:</strong> {ticketEmitido.pelicula}</p>
              <p><strong>HORA:</strong> {ticketEmitido.hora}</p>
              <p><strong>ENTRADAS:</strong> {ticketEmitido.entradas}</p>
              <p><strong>PAGO:</strong> {ticketEmitido.medioPago.toUpperCase()}</p>
              <hr style={{ border: 'none', borderTop: '1px dashed #2c080c', margin: '10px 0' }} />
              <p style={{ fontSize: '1.1rem' }}><strong>TOTAL: {ticketEmitido.total} €</strong></p>
              <p style={{ fontSize: '0.75rem', color: 'gray', marginTop: '10px', textAlign: 'center' }}>
                Ref: {ticketEmitido.id} · {ticketEmitido.horaEmision}
              </p>
            </div>
          ) : (
            <div>
              <h3 style={{ color: 'var(--burgundy)', margin: '0 0 6px' }}>Terminal de venta preparado</h3>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                Selecciona la sesión deseada y pulsa cobrar para generar el comprobante.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
