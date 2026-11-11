import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useCine } from '../../context/CineContext';
import cines from '../../assets/cines.json';

export default function PerfilPage() {
  const { user, logout, updateUser } = useAuth();
  const { setCineSeleccionado } = useCine();
  const navigate = useNavigate();

  const [entradas, setEntradas] = useState([]);
  const [nombre, setNombre] = useState(user?.name || '');
  const [email, setEmail] = useState(user?.email || '');
  const [telefono, setTelefono] = useState(user?.telefono || '');
  const [cineHabitualId, setCineHabitualId] = useState(user?.cineHabitualId || '');
  const [mensajeExito, setMensajeExito] = useState('');
  const [mensajeError, setMensajeError] = useState('');

  useEffect(() => {
    if (user) {
      setNombre(user.name || '');
      setEmail(user.email || '');
      setTelefono(user.telefono || '');
      setCineHabitualId(user.cineHabitualId || '');
    }
  }, [user]);

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

  function handleGuardarDatos(e) {
    e.preventDefault();
    if (!user) return;
    setMensajeError('');
    setMensajeExito('');

    const cleanNombre = nombre.trim();
    const cleanEmail = email.trim();

    if (!cleanNombre) {
      setMensajeError('El nombre y apellidos no pueden estar vacíos.');
      return;
    }
    if (!cleanEmail || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) {
      setMensajeError('Introduce un formato de correo electrónico válido.');
      return;
    }

    const cineObj = cines.find((c) => c.id === Number(cineHabitualId));
    const cineHabitualName = cineObj ? cineObj.name : '';

    updateUser({
      name: cleanNombre,
      email: cleanEmail,
      telefono: telefono.trim(),
      cineHabitualId: cineHabitualId ? Number(cineHabitualId) : null,
      cineHabitualName,
    });

    if (cineObj) {
      setCineSeleccionado(cineObj);
    }

    setMensajeExito('Datos personales y cine habitual guardados con éxito.');
    setTimeout(() => setMensajeExito(''), 3500);
  }

  function borrarHistorial() {
    localStorage.removeItem('cinasee_entradas');
    setEntradas([]);
  }

  const cineHabitualObj = cines.find((c) => c.id === Number(cineHabitualId));

  return (
    <div style={{ maxWidth: '960px', margin: '0 auto', padding: 'clamp(28px, 4vw, 56px) var(--gutter)' }}>
      {/* Cabecera del perfil */}
      <header style={{
        background: 'white',
        borderRadius: 'var(--radius-lg)',
        padding: '28px',
        boxShadow: 'var(--shadow-md)',
        border: '1px solid rgba(217, 164, 65, 0.3)',
        marginBottom: '28px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: '20px'
      }}>
        <div>
          <p className="home-eyebrow" style={{ margin: '0 0 4px' }}>Mi Cuenta CinASEE</p>
          <h1 style={{ fontSize: '2rem', color: 'var(--burgundy)', margin: '4px 0 6px' }}>
            {user ? user.name : 'Usuario invitado'}
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.92rem', margin: 0 }}>
            {user ? user.email : 'Sin sesión iniciada'} &nbsp;·&nbsp;
            Perfil: <strong style={{ color: 'var(--granate)' }}>{user ? user.rol : 'INVITADO'}</strong>
            {cineHabitualObj && (
              <>
                &nbsp;·&nbsp; Cine habitual: <strong style={{ color: 'var(--burgundy)' }}>{cineHabitualObj.name}</strong>
              </>
            )}
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

      {/* Sección: Edición de Datos Personales y Cine Habitual */}
      {user && (
        <section style={{
          background: 'white',
          borderRadius: 'var(--radius-lg)',
          padding: '28px',
          boxShadow: 'var(--shadow-md)',
          border: '1px solid rgba(217, 164, 65, 0.3)',
          marginBottom: '32px'
        }}>
          <div style={{ marginBottom: '18px' }}>
            <h2 style={{ fontSize: '1.45rem', color: 'var(--burgundy)', margin: '0 0 4px', fontWeight: 700 }}>
              Datos Personales y Cine Habitual
            </h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: 0 }}>
              Actualiza tu información y vincula tu cine de preferencia para acelerar la compra de entradas.
            </p>
          </div>

          {mensajeExito && (
            <div style={{
              background: 'rgba(40, 167, 69, 0.12)',
              border: '1px solid rgba(40, 167, 69, 0.35)',
              color: '#155724',
              padding: '10px 14px',
              borderRadius: '6px',
              marginBottom: '18px',
              fontSize: '0.9rem',
              fontWeight: 600
            }}>
              {mensajeExito}
            </div>
          )}

          {mensajeError && (
            <div style={{
              background: '#fde8e8',
              border: '1px solid #f8b4b4',
              color: '#9b1c1c',
              padding: '10px 14px',
              borderRadius: '6px',
              marginBottom: '18px',
              fontSize: '0.9rem',
              fontWeight: 600
            }}>
              {mensajeError}
            </div>
          )}

          <form onSubmit={handleGuardarDatos}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
              gap: '16px',
              marginBottom: '18px'
            }}>
              <div>
                <label className="admin-label" htmlFor="perfil-nombre">Nombre y Apellidos</label>
                <input
                  id="perfil-nombre"
                  type="text"
                  className="admin-input"
                  value={nombre}
                  onChange={(e) => setNombre(e.target.value)}
                  placeholder="Tu nombre completo"
                  required
                />
              </div>

              <div>
                <label className="admin-label" htmlFor="perfil-email">Correo Electrónico</label>
                <input
                  id="perfil-email"
                  type="email"
                  className="admin-input"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="ejemplo@correo.es"
                  required
                />
              </div>

              <div>
                <label className="admin-label" htmlFor="perfil-telefono">Teléfono de Contacto</label>
                <input
                  id="perfil-telefono"
                  type="tel"
                  className="admin-input"
                  value={telefono}
                  onChange={(e) => setTelefono(e.target.value)}
                  placeholder="600 000 000"
                />
              </div>

              <div>
                <label className="admin-label" htmlFor="perfil-cine">Cine Habitual</label>
                <select
                  id="perfil-cine"
                  className="admin-select"
                  value={cineHabitualId}
                  onChange={(e) => setCineHabitualId(e.target.value)}
                >
                  <option value="">-- Sin cine habitual asignado --</option>
                  {cines.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({c.city})
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div style={{
              background: 'rgba(217, 164, 65, 0.1)',
              border: '1px solid rgba(217, 164, 65, 0.3)',
              borderRadius: '6px',
              padding: '10px 14px',
              marginBottom: '20px',
              fontSize: '0.86rem',
              color: 'var(--dark-burgundy)'
            }}>
              Tu cine habitual se preseleccionará de forma automática en cartelera y en la compra de entradas, evitando tener que buscarlo y rellenarlo cada vez.
            </div>

            <button
              type="submit"
              className="home-button"
              style={{ padding: '0.7em 1.6em', fontSize: '0.95rem' }}
            >
              Guardar Cambios
            </button>
          </form>
        </section>
      )}

      {/* Sección Mis Entradas */}
      <section>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '18px' }}>
          <div>
            <h2 style={{ fontSize: '1.45rem', color: 'var(--burgundy)', margin: '0 0 4px', fontWeight: 700 }}>
              Entradas y Reservas
            </h2>
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
