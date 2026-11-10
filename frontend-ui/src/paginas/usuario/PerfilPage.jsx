import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export default function PerfilPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [entradas, setEntradas] = useState([]);

  useEffect(() => {
    try {
      const raw = localStorage.getItem('cinasee_entradas');
      if (raw) {
        setEntradas(JSON.parse(raw));
      }
    } catch {
      setEntradas([]);
    }
  }, []);

  function handleLogout() {
    logout();
    navigate('/');
  }

  function borrarHistorial() {
    localStorage.removeItem('cinasee_entradas');
    setEntradas([]);
  }

  return (
    <div style={{ maxWidth: '900px', margin: '0 auto', padding: 'clamp(28px, 4vw, 56px) var(--gutter)' }}>
      {/* Cabecera del perfil */}
      <header style={{
        background: 'white',
        borderRadius: 'var(--radius-lg)',
        padding: '28px',
        boxShadow: 'var(--shadow-md)',
        border: '1px solid rgba(217, 164, 65, 0.3)',
        marginBottom: '32px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: '20px'
      }}>
        <div>
          <p className="home-eyebrow">Mi Cuenta CinASEE</p>
          <h1 style={{ fontSize: '2rem', color: 'var(--burgundy)', margin: '4px 0 8px' }}>
            {user ? user.name : 'Usuario invitado'}
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem' }}>
            {user ? user.email : 'Sin sesión iniciada'} &nbsp;·&nbsp;
            Perfil: <strong style={{ color: 'var(--granate)' }}>{user ? user.rol : 'INVITADO'}</strong>
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          {user ? (
            <button
              className="home-button home-button-dark"
              type="button"
              onClick={handleLogout}
              style={{ padding: '0.6em 1.2em', fontSize: '0.9rem' }}
            >
              Cerrar sesión
            </button>
          ) : (
            <Link
              className="home-button"
              to="/login"
              style={{ padding: '0.6em 1.2em', fontSize: '0.9rem' }}
            >
              Iniciar sesión
            </Link>
          )}
        </div>
      </header>

      {/* Sección Mis Entradas */}
      <section>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '18px' }}>
          <div>
            <h2 style={{ fontSize: '1.6rem', color: 'var(--burgundy)', margin: '0 0 4px' }}>Entradas y Reservas</h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: 0 }}>
              Historial de compras y localizadores de acceso a sala
            </p>
          </div>
          {entradas.length > 0 && (
            <button
              type="button"
              onClick={borrarHistorial}
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--text-muted)',
                cursor: 'pointer',
                fontSize: '0.85rem',
                textDecoration: 'underline'
              }}
            >
              Limpiar historial
            </button>
          )}
        </div>

        {entradas.length === 0 ? (
          <div style={{
            background: 'white',
            borderRadius: 'var(--radius-lg)',
            padding: '40px 20px',
            textAlign: 'center',
            border: '1px dashed rgba(217, 164, 65, 0.4)',
            boxShadow: 'var(--shadow-md)'
          }}>
            <h3 style={{ color: 'var(--burgundy)', marginBottom: '8px' }}>No tienes entradas activas</h3>
            <p style={{ color: 'var(--text-muted)', marginBottom: '20px' }}>
              Consulta nuestra cartelera y reserva tus butacas en pocos pasos.
            </p>
            <Link className="home-button" to="/cartelera">
              Ver cartelera
            </Link>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            {entradas.map((ticket) => (
              <article
                key={ticket.id}
                style={{
                  background: 'white',
                  borderRadius: 'var(--radius-lg)',
                  border: '1px solid rgba(217, 164, 65, 0.3)',
                  boxShadow: 'var(--shadow-md)',
                  padding: '24px',
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
                  gap: '20px',
                  alignItems: 'center'
                }}
              >
                <div>
                  <span style={{
                    display: 'inline-block',
                    padding: '3px 8px',
                    borderRadius: '4px',
                    background: 'var(--grad-gold)',
                    color: 'var(--dark-burgundy)',
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    marginBottom: '8px'
                  }}>
                    Localizador: {ticket.id}
                  </span>
                  <h3 style={{ fontSize: '1.4rem', color: 'var(--burgundy)', margin: '0 0 6px' }}>
                    {ticket.pelicula}
                  </h3>
                  <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '8px' }}>
                    {ticket.cine} — {ticket.sala}
                  </p>
                  <p style={{ fontSize: '0.95rem', color: 'var(--dark-burgundy)', margin: '0 0 4px' }}>
                    Horario: <strong>{ticket.hora}</strong> &nbsp;|&nbsp; Fecha: {new Date(ticket.fecha).toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric', month: 'short' })}
                  </p>
                  <p style={{ fontSize: '0.95rem', color: 'var(--granate)', fontWeight: 600 }}>
                    Butacas: {ticket.butacas.join(', ')}
                  </p>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '8px' }}>
                    Importe: <strong>{ticket.total} €</strong>
                  </p>
                </div>

                <div style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: '16px',
                  borderRadius: 'var(--radius-sm)',
                  background: 'rgba(247, 235, 221, 0.5)',
                  border: '1px dashed rgba(217, 164, 65, 0.5)'
                }}>
                  <div style={{
                    width: '100px',
                    height: '100px',
                    background: '#2c080c',
                    color: '#f7ebdd',
                    display: 'grid',
                    placeItems: 'center',
                    fontFamily: 'monospace',
                    fontWeight: 700,
                    fontSize: '0.75rem',
                    borderRadius: '6px',
                    boxShadow: '0 4px 10px rgba(0,0,0,0.15)'
                  }}>
                    [QR TICKET]
                  </div>
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '8px', fontWeight: 600 }}>
                    Presentar en control de acceso
                  </span>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
