import { useState, useEffect } from 'react';
import { useAuth } from '../../context/useAuth';
import { useCine } from '../../context/useCine';
import { obtenerSolicitudesRol, revisarSolicitudRol, asignarRolUsuario } from '../../api/usuarios';
import { AvisoAdmin, AvisoSinToken } from './AdminAvisos';
import { Cargando } from '../../components/EstadoCarga';

const ETIQUETA_ROL = {
  CLIENTE_REGISTRADO: 'Cliente Registrado (USER)',
  STAFF_TAQUILLA: 'Personal de Taquilla (STAFF)',
  ADMIN: 'Administrador (ADMIN)',
};

const BADGE_ESTADO = {
  PENDING: { label: 'Pendiente', color: '#b45309', bg: '#fef3c7', border: '#fcd34d' },
  APPROVED: { label: 'Aprobada', color: '#15803d', bg: '#dcfce7', border: '#86efac' },
  REJECTED: { label: 'Rechazada', color: '#b91c1c', bg: '#fee2e2', border: '#fca5a5' },
};

export default function AdminSolicitudesPage() {
  const { user, tokenUsuario } = useAuth();
  const token = tokenUsuario();
  const { cines } = useCine();

  const [recarga, setRecarga] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('PENDING');
  const [filtroCine, setFiltroCine] = useState('');
  const [aviso, setAviso] = useState(null);

  // Lista de solicitudes cargadas y clave de sincronización
  const [solicitudesCargadas, setSolicitudesCargadas] = useState({ clave: null, lista: [] });
  const claveActual = `${token}|${filtroEstado}|${filtroCine}|${recarga}`;
  const solicitudes = solicitudesCargadas.clave === claveActual ? solicitudesCargadas.lista : [];
  const cargando = !!token && solicitudesCargadas.clave !== claveActual;

  // Modal de revisión de solicitud
  const [solicitudEnRevision, setSolicitudEnRevision] = useState(null);
  const [decision, setDecision] = useState('APPROVED'); // 'APPROVED' | 'REJECTED'
  const [comentario, setComentario] = useState('');
  const [procesando, setProcesando] = useState(false);

  // Modal de asignación directa de rol por ID
  const [modalAsignar, setModalAsignar] = useState(false);
  const [errorModalAsignar, setErrorModalAsignar] = useState(null);
  const [asignarForm, setAsignarForm] = useState({
    userId: '',
    role: 'STAFF_TAQUILLA',
    cinemaId: '',
    cinemaManager: false,
  });
  const [guardandoAsignacion, setGuardandoAsignacion] = useState(false);

  useEffect(() => {
    if (!token) return undefined;
    let cancelado = false;
    const clave = `${token}|${filtroEstado}|${filtroCine}|${recarga}`;
    const params = {};
    if (filtroEstado) params.status = filtroEstado;
    if (filtroCine) params.cinemaId = filtroCine;

    obtenerSolicitudesRol(params, token)
      .then((res) => {
        if (!cancelado) {
          setSolicitudesCargadas({ clave, lista: Array.isArray(res) ? res : [] });
        }
      })
      .catch((err) => {
        if (!cancelado) {
          setSolicitudesCargadas({ clave, lista: [] });
          setAviso({ tipo: 'error', texto: `Error al cargar solicitudes: ${err.message}` });
        }
      });

    return () => { cancelado = true; };
  }, [token, filtroEstado, filtroCine, recarga]);

  if (!token) return <AvisoSinToken />;

  function nombreCine(id) {
    if (!id) return 'Todos (Ámbito global)';
    return cines.find((c) => c.id === id)?.name || id;
  }

  function abrirRevision(sol, dec) {
    setSolicitudEnRevision(sol);
    setDecision(dec);
    setComentario('');
    setAviso(null);
  }

  async function handleConfirmarRevision(e) {
    e.preventDefault();
    if (!solicitudEnRevision) return;
    setProcesando(true);
    setAviso(null);
    try {
      await revisarSolicitudRol(
        solicitudEnRevision.id,
        {
          status: decision,
          reviewComment: comentario.trim() || undefined,
        },
        token
      );
      setAviso({
        tipo: 'ok',
        texto: `Solicitud #${solicitudEnRevision.id} ${decision === 'APPROVED' ? 'aprobada' : 'rechazada'} correctamente.`,
      });
      setSolicitudEnRevision(null);
      setRecarga((n) => n + 1);
    } catch (err) {
      setAviso({ tipo: 'error', texto: `No se pudo revisar la solicitud: ${err.message}` });
    } finally {
      setProcesando(false);
    }
  }

  async function handleAsignarRolDirecto(e) {
    e.preventDefault();
    if (!asignarForm.userId) return;
    setGuardandoAsignacion(true);
    setErrorModalAsignar(null);
    setAviso(null);
    try {
      await asignarRolUsuario(
        asignarForm.userId,
        {
          role: asignarForm.role,
          cinemaId: asignarForm.role === 'STAFF_TAQUILLA' ? (asignarForm.cinemaId || null) : null,
          cinemaManager: asignarForm.role === 'STAFF_TAQUILLA' ? !!asignarForm.cinemaManager : false,
        },
        token
      );
      setAviso({
        tipo: 'ok',
        texto: `Rol "${ETIQUETA_ROL[asignarForm.role] || asignarForm.role}" asignado correctamente al usuario #${asignarForm.userId}.`,
      });
      setModalAsignar(false);
      setAsignarForm({ userId: '', role: 'STAFF_TAQUILLA', cinemaId: '', cinemaManager: false });
      setRecarga((n) => n + 1);
    } catch (err) {
      setErrorModalAsignar(`Error al asignar rol: ${err.message}`);
    } finally {
      setGuardandoAsignacion(false);
    }
  }

  return (
    <div className="admin-page">
      <div className="admin-container">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px', marginBottom: '24px' }}>
          <div>
            <h1 className="admin-title" style={{ margin: 0 }}>Gestión de Solicitudes de Rol</h1>
            <p style={{ margin: '4px 0 0', color: 'var(--text-muted)', fontSize: '0.92rem' }}>
              Revisa y gestiona las solicitudes de acceso para puestos de taquilla y administración.
            </p>
          </div>
          <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            {user?.rol === 'ADMIN' && (
              <button
                type="button"
                className="admin-btn admin-btn-primary"
                onClick={() => { setErrorModalAsignar(null); setModalAsignar(true); }}
              >
                + Asignar Rol por ID
              </button>
            )}
            <button
              type="button"
              className="admin-btn admin-btn-secondary"
              onClick={() => setRecarga((n) => n + 1)}
              disabled={cargando}
            >
              Actualizar lista
            </button>
          </div>
        </div>

        {aviso && <AvisoAdmin tipo={aviso.tipo} texto={aviso.texto} onCerrar={() => setAviso(null)} />}

        {/* Filtros */}
        <div className="admin-panel" style={{ marginBottom: '20px', padding: '16px 20px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px', alignItems: 'end' }}>
            <div>
              <label className="admin-label" htmlFor="filtro-estado">Estado de la solicitud</label>
              <select
                id="filtro-estado"
                className="admin-select"
                value={filtroEstado}
                onChange={(e) => setFiltroEstado(e.target.value)}
              >
                <option value="">Todas las solicitudes</option>
                <option value="PENDING">Pendientes de revisión</option>
                <option value="APPROVED">Aprobadas</option>
                <option value="REJECTED">Rechazadas</option>
              </select>
            </div>

            {user?.rol === 'ADMIN' && (
              <div>
                <label className="admin-label" htmlFor="filtro-cine">Filtrar por Cine</label>
                <select
                  id="filtro-cine"
                  className="admin-select"
                  value={filtroCine}
                  onChange={(e) => setFiltroCine(e.target.value)}
                >
                  <option value="">Todos los cines</option>
                  {cines.map((c) => (
                    <option key={c.id} value={c.id}>{c.name} ({c.city})</option>
                  ))}
                </select>
              </div>
            )}
          </div>
        </div>

        {/* Modal: Asignar Rol Directamente por ID */}
        {modalAsignar && (
          <div style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '20px',
          }} onClick={() => setModalAsignar(false)}>
            <div style={{
              background: 'white',
              borderRadius: 'var(--radius-lg)',
              maxWidth: '500px',
              width: '100%',
              padding: '28px',
              boxShadow: 'var(--shadow-lg)',
            }} onClick={(e) => e.stopPropagation()}>
              <h2 style={{ fontSize: '1.3rem', color: 'var(--burgundy)', margin: '0 0 8px' }}>
                Asignar Rol por ID de Usuario
              </h2>
              <p style={{ fontSize: '0.88rem', color: 'var(--text-muted)', marginBottom: '18px' }}>
                Modifica directamente el rol de cualquier cuenta introduciendo su identificador de usuario.
              </p>

              {errorModalAsignar && (
                <div style={{
                  color: '#991b1b',
                  background: '#fee2e2',
                  border: '1px solid #f87171',
                  borderRadius: '6px',
                  padding: '10px 14px',
                  marginBottom: '16px',
                  fontSize: '0.88rem',
                }}>
                  {errorModalAsignar}
                </div>
              )}

              <form onSubmit={handleAsignarRolDirecto}>
                <div style={{ marginBottom: '16px' }}>
                  <label className="admin-label" htmlFor="asignar-user-id">Número / ID de Usuario *</label>
                  <input
                    id="asignar-user-id"
                    type="number"
                    min="1"
                    className="admin-input"
                    required
                    placeholder="Ej: 4"
                    value={asignarForm.userId}
                    onChange={(e) => setAsignarForm({ ...asignarForm, userId: e.target.value })}
                  />
                </div>

                <div style={{ marginBottom: '16px' }}>
                  <label className="admin-label" htmlFor="asignar-rol">Rol a Asignar *</label>
                  <select
                    id="asignar-rol"
                    className="admin-select"
                    required
                    value={asignarForm.role}
                    onChange={(e) => setAsignarForm({ ...asignarForm, role: e.target.value })}
                  >
                    <option value="STAFF_TAQUILLA">Personal de Taquilla (STAFF)</option>
                    <option value="ADMIN">Administrador (ADMIN)</option>
                    <option value="CLIENTE_REGISTRADO">Cliente Registrado (USER)</option>
                  </select>
                </div>

                {asignarForm.role === 'STAFF_TAQUILLA' && (
                  <>
                    <div style={{ marginBottom: '16px' }}>
                      <label className="admin-label" htmlFor="asignar-cine">Cine Asignado *</label>
                      <select
                        id="asignar-cine"
                        className="admin-select"
                        required
                        value={asignarForm.cinemaId}
                        onChange={(e) => setAsignarForm({ ...asignarForm, cinemaId: e.target.value })}
                      >
                        <option value="">Selecciona un cine…</option>
                        {cines.map((c) => (
                          <option key={c.id} value={c.id}>{c.name} ({c.city})</option>
                        ))}
                      </select>
                    </div>

                    <div style={{ marginBottom: '18px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <input
                        type="checkbox"
                        id="asignar-manager"
                        checked={asignarForm.cinemaManager}
                        onChange={(e) => setAsignarForm({ ...asignarForm, cinemaManager: e.target.checked })}
                        style={{ cursor: 'pointer' }}
                      />
                      <label htmlFor="asignar-manager" style={{ cursor: 'pointer', fontSize: '0.88rem', fontWeight: 600 }}>
                        Taquillero responsable (Manager de cine)
                      </label>
                    </div>
                  </>
                )}

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
                  <button
                    type="button"
                    className="admin-btn admin-btn-secondary"
                    onClick={() => setModalAsignar(false)}
                    disabled={guardandoAsignacion}
                  >
                    Cancelar
                  </button>
                  <button
                    type="submit"
                    className="admin-btn admin-btn-primary"
                    disabled={guardandoAsignacion}
                  >
                    {guardandoAsignacion ? 'Asignando…' : 'Asignar Rol'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Modal de decisión (Aprobar / Rechazar) */}
        {solicitudEnRevision && (
          <div style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '20px',
          }}>
            <div style={{
              background: 'white',
              borderRadius: 'var(--radius-lg)',
              maxWidth: '520px',
              width: '100%',
              padding: '28px',
              boxShadow: 'var(--shadow-lg)',
            }}>
              <h2 style={{ fontSize: '1.3rem', color: 'var(--burgundy)', margin: '0 0 8px' }}>
                {decision === 'APPROVED' ? 'Aprobar Solicitud' : 'Rechazar Solicitud'} #{solicitudEnRevision.id}
              </h2>
              <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', marginBottom: '16px' }}>
                Usuario ID: <strong>#{solicitudEnRevision.userId}</strong>, rol: <strong>{ETIQUETA_ROL[solicitudEnRevision.requestedRole] || solicitudEnRevision.requestedRole}</strong>
                {solicitudEnRevision.cinemaId && <>, cine: <strong>{nombreCine(solicitudEnRevision.cinemaId)}</strong></>}
              </p>

              {solicitudEnRevision.motivation && (
                <div style={{ background: '#fdf8f0', border: '1px solid #f2e2c8', borderRadius: 'var(--radius-sm)', padding: '10px 14px', fontSize: '0.88rem', marginBottom: '16px' }}>
                  <strong>Motivo alegado:</strong> {solicitudEnRevision.motivation}
                </div>
              )}

              <form onSubmit={handleConfirmarRevision}>
                <div style={{ marginBottom: '18px' }}>
                  <label className="admin-label" htmlFor="rev-comentario">Comentario de revisión (opcional)</label>
                  <textarea
                    id="rev-comentario"
                    className="admin-input"
                    rows={3}
                    placeholder="Ej. Aprobado tras comprobación de documentación interna."
                    value={comentario}
                    onChange={(e) => setComentario(e.target.value)}
                  />
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                  <button
                    type="button"
                    className="admin-btn admin-btn-secondary"
                    onClick={() => setSolicitudEnRevision(null)}
                    disabled={procesando}
                  >
                    Cancelar
                  </button>
                  <button
                    type="submit"
                    className={`admin-btn ${decision === 'APPROVED' ? 'admin-btn-primary' : 'admin-btn-danger'}`}
                    disabled={procesando}
                  >
                    {procesando ? 'Procesando…' : (decision === 'APPROVED' ? 'Confirmar Aprobación' : 'Confirmar Rechazo')}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Listado */}
        {cargando ? (
          <Cargando texto="Consultando solicitudes de rol…" />
        ) : solicitudes.length === 0 ? (
          <div className="admin-panel" style={{ textAlign: 'center', padding: '48px 20px', color: 'var(--text-muted)' }}>
            No se han encontrado solicitudes con los filtros seleccionados.
          </div>
        ) : (
          <div className="admin-panel admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Nº</th>
                  <th>Usuario</th>
                  <th>Rol Solicitado</th>
                  <th>Cine</th>
                  <th>Motivo</th>
                  <th>Fecha</th>
                  <th>Estado</th>
                  <th style={{ textAlign: 'right' }}>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {solicitudes.map((sol) => {
                  const badge = BADGE_ESTADO[sol.status] || { label: sol.status, color: '#374151', bg: '#f3f4f6', border: '#e5e7eb' };
                  const fecha = new Date(sol.createdAt);
                  return (
                    <tr key={sol.id}>
                      <td style={{ fontWeight: 700 }}>#{sol.id}</td>
                      <td>
                        <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>Usuario #{sol.userId}</span>
                      </td>
                      <td>
                        <strong>{ETIQUETA_ROL[sol.requestedRole] || sol.requestedRole}</strong>
                      </td>
                      <td>{nombreCine(sol.cinemaId)}</td>
                      <td style={{ maxWidth: '240px', fontSize: '0.86rem', color: 'var(--text-muted)' }}>
                        {sol.motivation || '-'}
                      </td>
                      <td style={{ fontSize: '0.84rem', whiteSpace: 'nowrap' }}>
                        {fecha.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' })}
                      </td>
                      <td>
                        <span style={{
                          display: 'inline-block',
                          padding: '3px 10px',
                          borderRadius: '12px',
                          fontSize: '0.78rem',
                          fontWeight: 700,
                          color: badge.color,
                          background: badge.bg,
                          border: `1px solid ${badge.border}`,
                        }}>
                          {badge.label}
                        </span>
                        {sol.reviewComment && (
                          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                            Nota: {sol.reviewComment}
                          </div>
                        )}
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        {sol.status === 'PENDING' ? (
                          <div style={{ display: 'inline-flex', gap: '8px' }}>
                            <button
                              type="button"
                              className="admin-btn admin-btn-sm"
                              style={{ background: '#15803d', color: 'white', borderColor: '#15803d' }}
                              onClick={() => abrirRevision(sol, 'APPROVED')}
                            >
                              Aprobar
                            </button>
                            <button
                              type="button"
                              className="admin-btn admin-btn-danger admin-btn-sm"
                              onClick={() => abrirRevision(sol, 'REJECTED')}
                            >
                              Rechazar
                            </button>
                          </div>
                        ) : (
                          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                            {sol.reviewedAt ? `Revisada el ${new Date(sol.reviewedAt).toLocaleDateString('es-ES')}` : 'Procesada'}
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
