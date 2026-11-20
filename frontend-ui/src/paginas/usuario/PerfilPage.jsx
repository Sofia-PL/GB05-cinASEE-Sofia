import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';
import { useCine } from '../../context/useCine';
import { misReservas, cancelarReserva, nombreButaca } from '../../api/reservas';
import { obtenerPelicula, obtenerSala, GENEROS, nombreGenero } from '../../api/cartelera';
import { obtenerRecomendaciones } from '../../api/analitica';
import { obtenerMisSolicitudesRol, crearSolicitudRol } from '../../api/usuarios';
import CodigoQR from '../../components/CodigoQR';
import { Cargando, ErrorCarga } from '../../components/EstadoCarga';

const tarjeta = {
  background: 'white',
  borderRadius: 'var(--radius-lg)',
  padding: '28px',
  boxShadow: 'var(--shadow-md)',
  border: '1px solid rgba(217, 164, 65, 0.3)',
  marginBottom: '28px',
};

const ESTADOS_RESERVA = {
  CONFIRMED: 'Pagada',
  PENDING_PAYMENT: 'Pendiente de pago',
  CANCELLED: 'Cancelada',
  EXPIRED: 'Caducada',
  REFUNDED: 'Devuelta',
};

/** Reservas del usuario (Comp. 2) con título de la película (Comp. 1) y sala */
async function cargarEntradas(token) {
  const reservas = (await misReservas(token)).filter((r) => r.status === 'CONFIRMED' || r.status === 'REFUNDED');
  const titulos = {};
  const salas = {};
  await Promise.all([
    ...[...new Set(reservas.map((r) => r.movieId))].map(async (id) => {
      try { titulos[id] = (await obtenerPelicula(id)).title; } catch { titulos[id] = 'Película'; }
    }),
    ...[...new Set(reservas.map((r) => r.roomId))].map(async (id) => {
      try { salas[id] = await obtenerSala(id, token); } catch { salas[id] = null; }
    }),
  ]);
  return reservas
    .map((r) => ({
      id: r.id,
      estado: r.status,
      pelicula: titulos[r.movieId],
      sala: salas[r.roomId]?.name || 'Sala',
      cinemaId: salas[r.roomId]?.cinemaId || null,
      inicio: r.showStartTime,
      futura: new Date(r.showStartTime).getTime() > Date.now(),
      butacas: (r.seats || []).map((s) => ({ nombre: nombreButaca(s.row, s.column), codigo: s.ticketCode })),
      total: Number(r.totalAmount).toFixed(2),
    }))
    .sort((a, b) => String(b.inicio).localeCompare(String(a.inicio)));
}

export default function PerfilPage() {
  const { user, logout, actualizarPerfil, tokenUsuario } = useAuth();
  const token = tokenUsuario();
  const { cines, setCineSeleccionado } = useCine();
  const navigate = useNavigate();

  // Formulario: datos del perfil del Comp. 3 (la página exige sesión, ver RutaProtegida)
  const [nombre, setNombre] = useState(user?.name || '');
  const [telefono, setTelefono] = useState(user?.phone || '');
  const [cineHabitualId, setCineHabitualId] = useState(user?.homeCinema || '');
  const [generos, setGeneros] = useState(user?.preferredGenres || []);
  const [guardando, setGuardando] = useState(false);
  const [mensaje, setMensaje] = useState(null);

  const [entradas, setEntradas] = useState({ cargando: true, error: '', lista: [] });
  const [recargaEntradas, setRecargaEntradas] = useState(0);
  const [recomendaciones, setRecomendaciones] = useState([]);

  // Solicitudes de cambio de rol (Comp. 3)
  const [solicitudesRol, setSolicitudesRol] = useState([]);
  const [recargaSolicitudes, setRecargaSolicitudes] = useState(0);
  const [rolSolicitado, setRolSolicitado] = useState(user?.rol === 'STAFF' ? 'ADMIN' : 'STAFF_TAQUILLA');
  const [cineSolicitadoId, setCineSolicitadoId] = useState('');
  const [motivoSolicitud, setMotivoSolicitud] = useState('');
  const [enviandoSolicitud, setEnviandoSolicitud] = useState(false);
  const [avisoSolicitud, setAvisoSolicitud] = useState(null);

  useEffect(() => {
    if (!token || user?.rol === 'ADMIN') return undefined;
    let cancelado = false;
    obtenerMisSolicitudesRol(token)
      .then((res) => { if (!cancelado) setSolicitudesRol(Array.isArray(res) ? res : []); })
      .catch(() => { if (!cancelado) setSolicitudesRol([]); });
    return () => { cancelado = true; };
  }, [token, user?.rol, recargaSolicitudes]);

  useEffect(() => {
    if (!token) return undefined;
    let cancelado = false;
    cargarEntradas(token)
      .then((lista) => { if (!cancelado) setEntradas({ cargando: false, error: '', lista }); })
      .catch((err) => { if (!cancelado) setEntradas({ cargando: false, error: err.message, lista: [] }); });
    return () => { cancelado = true; };
  }, [token, recargaEntradas]);

  // Recomendaciones de Analítica (Comp. 4) para clientes registrados
  useEffect(() => {
    if (!token || user?.rol !== 'USER') return undefined;
    let cancelado = false;
    obtenerRecomendaciones(user.id, token)
      .then((res) => { if (!cancelado) setRecomendaciones(res?.recommendations || []); })
      .catch(() => { /* sin recomendaciones: la sección no se muestra */ });
    return () => { cancelado = true; };
  }, [token, user?.id, user?.rol]);

  function handleLogout() {
    logout();
    navigate('/');
  }

  function toggleGenero(g) {
    setGeneros((prev) => (prev.includes(g) ? prev.filter((x) => x !== g) : [...prev, g]));
  }

  async function handleGuardarDatos(e) {
    e.preventDefault();
    setMensaje(null);
    const cleanNombre = nombre.trim();
    if (!cleanNombre) {
      setMensaje({ tipo: 'error', texto: 'El nombre y apellidos no pueden estar vacíos.' });
      return;
    }
    setGuardando(true);
    try {
      await actualizarPerfil({
        fullName: cleanNombre,
        phone: telefono.trim(),
        homeCinema: cineHabitualId,
        preferredGenres: generos,
      });
      const cine = cines.find((c) => c.id === cineHabitualId);
      if (cine) setCineSeleccionado(cine);
      setMensaje({ tipo: 'ok', texto: 'Datos guardados en tu cuenta.' });
    } catch (err) {
      setMensaje({ tipo: 'error', texto: `No se pudieron guardar los datos: ${err.message}` });
    } finally {
      setGuardando(false);
    }
  }

  async function handleCancelar(entrada) {
    if (!window.confirm(`¿Cancelar la reserva nº ${entrada.id} y solicitar la devolución de ${entrada.total} €?`)) return;
    try {
      await cancelarReserva(entrada.id, token);
      setRecargaEntradas((n) => n + 1);
    } catch (err) {
      setEntradas((prev) => ({ ...prev, error: `No se pudo cancelar la reserva: ${err.message}` }));
    }
  }

  async function handleCrearSolicitudRol(e) {
    e.preventDefault();
    setAvisoSolicitud(null);
    setEnviandoSolicitud(true);
    try {
      const payload = {
        requestedRole: rolSolicitado,
        motivation: motivoSolicitud.trim() || undefined,
        cinemaId: rolSolicitado === 'STAFF_TAQUILLA' ? (cineSolicitadoId || cines[0]?.id) : undefined,
      };
      await crearSolicitudRol(payload, token);
      setAvisoSolicitud({
        tipo: 'ok',
        texto: 'Solicitud enviada correctamente. El equipo de administración la revisará en breve.',
      });
      setMotivoSolicitud('');
      setRecargaSolicitudes((n) => n + 1);
    } catch (err) {
      setAvisoSolicitud({ tipo: 'error', texto: err.message || 'No se pudo enviar la solicitud.' });
    } finally {
      setEnviandoSolicitud(false);
    }
  }

  const cineHabitual = cines.find((c) => c.id === user?.homeCinema);
  const nombreCine = (id) => cines.find((c) => c.id === id)?.name || 'CinASEE';

  return (
    <div style={{ maxWidth: '960px', margin: '0 auto', padding: 'clamp(28px, 4vw, 56px) var(--gutter)' }}>
      {/* Cabecera del perfil */}
      <header style={{ ...tarjeta, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '20px' }}>
        <div>
          <p className="home-eyebrow" style={{ margin: '0 0 4px' }}>Mi Cuenta CinASEE</p>
          <h1 style={{ fontSize: '2rem', color: 'var(--burgundy)', margin: '4px 0 6px' }}>{user.name}</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.92rem', margin: 0 }}>
            {user.email} &nbsp;·&nbsp; Nº de cliente: <strong style={{ color: 'var(--burgundy)' }}>#{user.id}</strong> &nbsp;·&nbsp; Puntos de fidelización: <strong style={{ color: 'var(--granate)' }}>{user.loyaltyPoints}</strong>
            {cineHabitual && (
              <> &nbsp;·&nbsp; Cine habitual: <strong style={{ color: 'var(--burgundy)' }}>{cineHabitual.name}</strong></>
            )}
          </p>
        </div>
        <button
          className="home-button home-button-dark"
          type="button"
          onClick={handleLogout}
          style={{ padding: '0.6em 1.2em', fontSize: '0.9rem' }}
        >
          Cerrar sesión
        </button>
      </header>

      {/* Datos personales, cine habitual y géneros favoritos (Comp. 3) */}
      <section style={tarjeta}>
        <div style={{ marginBottom: '18px' }}>
          <h2 style={{ fontSize: '1.45rem', color: 'var(--burgundy)', margin: '0 0 4px', fontWeight: 700 }}>
            Datos Personales y Preferencias
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: 0 }}>
            Tu cine habitual se preselecciona en la cartelera y tus géneros favoritos mejoran las recomendaciones.
          </p>
        </div>

        {mensaje && (
          <div className={mensaje.tipo === 'ok' ? 'cinasee-aviso cinasee-aviso--ok' : 'cinasee-error'} role={mensaje.tipo === 'ok' ? 'status' : 'alert'}>
            {mensaje.texto}
          </div>
        )}

        <form onSubmit={handleGuardarDatos}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '16px', marginBottom: '18px' }}>
            <div>
              <label className="admin-label" htmlFor="perfil-userid">Nº de Cliente (para taquilla)</label>
              <input id="perfil-userid" type="text" className="admin-input" value={`#${user.id}`} disabled style={{ fontWeight: 700, color: 'var(--granate)', background: '#fdf8f0' }} />
            </div>

            <div>
              <label className="admin-label" htmlFor="perfil-nombre">Nombre y Apellidos</label>
              <input
                id="perfil-nombre"
                type="text"
                className="admin-input"
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                required
              />
            </div>

            <div>
              <label className="admin-label" htmlFor="perfil-email">Correo Electrónico</label>
              <input id="perfil-email" type="email" className="admin-input" value={user.email} disabled />
            </div>

            <div>
              <label className="admin-label" htmlFor="perfil-telefono">Teléfono de Contacto</label>
              <input
                id="perfil-telefono"
                type="tel"
                className="admin-input"
                maxLength={30}
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
                <option value="">-- Sin cine habitual --</option>
                {cines.map((c) => (
                  <option key={c.id} value={c.id}>{c.name} ({c.city})</option>
                ))}
              </select>
            </div>
          </div>

          <fieldset style={{ border: 'none', padding: 0, margin: '0 0 20px' }}>
            <legend className="admin-label">Géneros favoritos</legend>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
              {Object.keys(GENEROS).map((g) => (
                <label key={g} className="cine-format-pill" style={{ cursor: 'pointer', display: 'inline-flex', gap: '6px', alignItems: 'center' }}>
                  <input type="checkbox" checked={generos.includes(g)} onChange={() => toggleGenero(g)} />
                  {nombreGenero(g)}
                </label>
              ))}
            </div>
          </fieldset>

          <button type="submit" className="home-button" disabled={guardando} style={{ padding: '0.7em 1.6em', fontSize: '0.95rem' }}>
            {guardando ? 'Guardando…' : 'Guardar Cambios'}
          </button>
        </form>
      </section>

      {/* Recomendaciones (Comp. 4) */}
      {recomendaciones.length > 0 && (
        <section style={tarjeta}>
          <h2 style={{ fontSize: '1.45rem', color: 'var(--burgundy)', margin: '0 0 14px', fontWeight: 700 }}>
            Recomendadas para ti
          </h2>
          <ul style={{ margin: 0, paddingLeft: '18px', lineHeight: 1.7 }}>
            {recomendaciones.map((r) => (
              <li key={r.movieId}>
                <strong>{r.title}</strong> — <span style={{ color: 'var(--text-muted)' }}>{r.reason}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Solicitud de cambio de rol (Comp. 3) — Solo para clientes o staff */}
      {user.rol !== 'ADMIN' && (
        <section style={tarjeta}>
          <div style={{ marginBottom: '18px' }}>
            <h2 style={{ fontSize: '1.45rem', color: 'var(--burgundy)', margin: '0 0 4px', fontWeight: 700 }}>
              Solicitud de Cambio de Rol / Permisos
            </h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: 0 }}>
              Si necesitas acceso como personal de taquilla o administrador de CinASEE, puedes enviar tu solicitud aquí para que el equipo la revise.
            </p>
          </div>

          {avisoSolicitud && (
            <div className={avisoSolicitud.tipo === 'ok' ? 'cinasee-aviso cinasee-aviso--ok' : 'cinasee-error'} role="alert">
              {avisoSolicitud.texto}
            </div>
          )}

          {/* Historial de solicitudes */}
          {solicitudesRol.length > 0 && (
            <div style={{ marginBottom: '24px' }}>
              <h3 style={{ fontSize: '1.05rem', color: 'var(--burgundy)', marginBottom: '12px' }}>
                Tus solicitudes anteriores
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {solicitudesRol.map((sol) => {
                  const estadoBadge = {
                    PENDING: { label: 'Pendiente de revisión', color: '#b45309', bg: '#fef3c7', border: '#fcd34d' },
                    APPROVED: { label: 'Aprobada', color: '#15803d', bg: '#dcfce7', border: '#86efac' },
                    REJECTED: { label: 'Rechazada', color: '#b91c1c', bg: '#fee2e2', border: '#fca5a5' },
                  }[sol.status] || { label: sol.status, color: '#374151', bg: '#f3f4f6', border: '#e5e7eb' };

                  return (
                    <div
                      key={sol.id}
                      style={{
                        padding: '14px 18px',
                        borderRadius: 'var(--radius-sm)',
                        background: '#fbf9f6',
                        border: '1px solid rgba(217, 164, 65, 0.25)',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        flexWrap: 'wrap',
                        gap: '10px',
                      }}
                    >
                      <div>
                        <div style={{ fontWeight: 700, color: 'var(--dark-burgundy)', fontSize: '0.95rem' }}>
                          Solicitud #{sol.id} · Rol pedido: {sol.requestedRole === 'STAFF_TAQUILLA' ? 'Personal de Taquilla' : 'Administrador'}
                          {sol.cinemaId && ` (${nombreCine(sol.cinemaId)})`}
                        </div>
                        {sol.motivation && (
                          <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                            Motivo: {sol.motivation}
                          </div>
                        )}
                        {sol.reviewComment && (
                          <div style={{ fontSize: '0.84rem', color: 'var(--granate)', marginTop: '4px', fontWeight: 600 }}>
                            Comentario de revisión: {sol.reviewComment}
                          </div>
                        )}
                        <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                          Fecha: {new Date(sol.createdAt).toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })}
                        </div>
                      </div>

                      <div style={{ textAlign: 'right' }}>
                        <span style={{
                          display: 'inline-block',
                          padding: '4px 10px',
                          borderRadius: '12px',
                          fontSize: '0.8rem',
                          fontWeight: 700,
                          color: estadoBadge.color,
                          background: estadoBadge.bg,
                          border: `1px solid ${estadoBadge.border}`,
                        }}>
                          {estadoBadge.label}
                        </span>
                        {sol.status === 'APPROVED' && (
                          <div style={{ fontSize: '0.75rem', color: '#15803d', marginTop: '4px', maxWidth: '200px' }}>
                            Cierra sesión e inicia de nuevo para activar los permisos.
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Formulario de nueva solicitud (solo si no hay una PENDING) */}
          {solicitudesRol.some((s) => s.status === 'PENDING') ? (
            <div className="cinasee-aviso" style={{ background: '#fef3c7', borderColor: '#fcd34d', color: '#92400e' }}>
              Ya tienes una solicitud en estado pendiente de revisión. Cuando el administrador la resuelva podrás solicitar un cambio adicional si fuera necesario.
            </div>
          ) : (
            <form onSubmit={handleCrearSolicitudRol}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '16px', marginBottom: '16px' }}>
                <div>
                  <label className="admin-label" htmlFor="sol-rol">Rol deseado</label>
                  <select
                    id="sol-rol"
                    className="admin-select"
                    value={rolSolicitado}
                    onChange={(e) => setRolSolicitado(e.target.value)}
                  >
                    {user.rol === 'USER' && (
                      <option value="STAFF_TAQUILLA">Personal de Taquilla (STAFF_TAQUILLA)</option>
                    )}
                    <option value="ADMIN">Administrador (ADMIN)</option>
                  </select>
                </div>

                {rolSolicitado === 'STAFF_TAQUILLA' && (
                  <div>
                    <label className="admin-label" htmlFor="sol-cine">Cine de asignación</label>
                    <select
                      id="sol-cine"
                      className="admin-select"
                      value={cineSolicitadoId || cines[0]?.id || ''}
                      onChange={(e) => setCineSolicitadoId(e.target.value)}
                    >
                      {cines.map((c) => (
                        <option key={c.id} value={c.id}>{c.name} ({c.city})</option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              <div style={{ marginBottom: '16px' }}>
                <label className="admin-label" htmlFor="sol-motivo">Motivo o justificación</label>
                <textarea
                  id="sol-motivo"
                  className="admin-input"
                  rows={2}
                  placeholder="Describe brevemente el motivo de tu solicitud o tu puesto en el cine..."
                  value={motivoSolicitud}
                  onChange={(e) => setMotivoSolicitud(e.target.value)}
                />
              </div>

              <button
                type="submit"
                className="home-button"
                disabled={enviandoSolicitud}
                style={{ padding: '0.65em 1.4em', fontSize: '0.92rem' }}
              >
                {enviandoSolicitud ? 'Enviando solicitud…' : 'Enviar Solicitud de Rol'}
              </button>
            </form>
          )}
        </section>
      )}

      {/* Entradas (Comp. 2) */}
      <section>
        <div style={{ marginBottom: '18px' }}>
          <h2 style={{ fontSize: '1.45rem', color: 'var(--burgundy)', margin: '0 0 4px', fontWeight: 700 }}>
            Entradas y Reservas
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: 0 }}>
            Presenta el código QR de cada butaca en el control de acceso.
          </p>
        </div>

        {entradas.cargando && <Cargando texto="Cargando tus entradas…" />}
        {entradas.error && <ErrorCarga mensaje={entradas.error} onReintentar={() => setRecargaEntradas((n) => n + 1)} />}

        {!entradas.cargando && !entradas.error && entradas.lista.length === 0 && (
          <div style={{ ...tarjeta, textAlign: 'center', border: '1px dashed rgba(217, 164, 65, 0.4)' }}>
            <h3 style={{ color: 'var(--burgundy)', marginBottom: '8px' }}>Todavía no tienes entradas</h3>
            <p style={{ color: 'var(--text-muted)', marginBottom: '20px' }}>
              Consulta nuestra cartelera y reserva tus butacas en pocos pasos.
            </p>
            <Link className="home-button" to="/cartelera">Ver cartelera</Link>
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {entradas.lista.map((e) => {
            const inicio = new Date(e.inicio);
            return (
              <article key={e.id} style={{ ...tarjeta, marginBottom: 0, padding: '24px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
                  <div>
                    <span style={{
                      display: 'inline-block',
                      padding: '3px 8px',
                      borderRadius: '4px',
                      background: 'var(--grad-gold)',
                      color: 'var(--dark-burgundy)',
                      fontSize: '0.75rem',
                      fontWeight: 700,
                      marginBottom: '8px',
                    }}>
                      Reserva nº {e.id} · {ESTADOS_RESERVA[e.estado] || e.estado}
                    </span>
                    <h3 style={{ fontSize: '1.4rem', color: 'var(--burgundy)', margin: '0 0 6px' }}>{e.pelicula}</h3>
                    <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '8px' }}>
                      {nombreCine(e.cinemaId)} — {e.sala}
                    </p>
                    <p style={{ fontSize: '0.95rem', color: 'var(--dark-burgundy)', margin: '0 0 4px' }}>
                      {inicio.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' })} ·{' '}
                      <strong>{inicio.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })}</strong>
                    </p>
                    <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '8px' }}>
                      Importe: <strong>{e.total} €</strong>
                    </p>
                  </div>
                  {e.estado === 'CONFIRMED' && e.futura && (
                    <button
                      type="button"
                      className="home-button home-button-dark"
                      onClick={() => handleCancelar(e)}
                      style={{ alignSelf: 'flex-start', padding: '0.5em 1em', fontSize: '0.85rem' }}
                    >
                      Cancelar y devolver
                    </button>
                  )}
                </div>

                {e.estado === 'CONFIRMED' && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '16px', marginTop: '16px' }}>
                    {e.butacas.map((b) => (
                      <div
                        key={b.codigo || b.nombre}
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          gap: '6px',
                          padding: '12px',
                          borderRadius: 'var(--radius-sm)',
                          background: 'rgba(247, 235, 221, 0.5)',
                          border: '1px dashed rgba(217, 164, 65, 0.5)',
                        }}
                      >
                        <strong style={{ color: 'var(--granate)' }}>Butaca {b.nombre}</strong>
                        {b.codigo && <CodigoQR texto={b.codigo} />}
                        {b.codigo && <code className="perfil-codigo-entrada">{b.codigo}</code>}
                      </div>
                    ))}
                  </div>
                )}
              </article>
            );
          })}
        </div>
      </section>
    </div>
  );
}
