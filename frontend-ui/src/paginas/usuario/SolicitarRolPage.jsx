import { useState, useEffect } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';
import { useCine } from '../../context/useCine';
import { obtenerMisSolicitudesRol, crearSolicitudRol } from '../../api/usuarios';

/* =========================================================
   Solicitud de cambio de rol (Comp. 3)
   ---------------------------------------------------------
   Antes estaba dentro de "Mi cuenta". Se ha movido a su propia
   página porque la mayoría de clientes no la necesita: desde el
   perfil solo queda un enlace discreto.
   ========================================================= */

const tarjeta = {
  background: 'white',
  borderRadius: 'var(--radius-lg)',
  padding: '28px',
  boxShadow: 'var(--shadow-md)',
  border: '1px solid rgba(217, 164, 65, 0.3)',
  marginBottom: '28px',
};

export default function SolicitarRolPage() {
  const { user, tokenUsuario } = useAuth();
  const token = tokenUsuario();
  const { cines } = useCine();

  // Solicitudes de cambio de rol del usuario
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

  const nombreCine = (id) => cines.find((c) => c.id === id)?.name || 'CinASEE';

  // Un administrador ya tiene todos los permisos: no hay nada que solicitar
  if (user.rol === 'ADMIN') return <Navigate to="/perfil" replace />;

  return (
    <div style={{ maxWidth: '960px', margin: '0 auto', padding: 'clamp(28px, 4vw, 56px) var(--gutter)' }}>
      <Link to="/perfil" style={{ display: 'inline-block', marginBottom: '14px', color: 'var(--burgundy)' }}>
        Volver a mi cuenta
      </Link>
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
                        Solicitud #{sol.id}, rol pedido: {sol.requestedRole === 'STAFF_TAQUILLA' ? 'Personal de Taquilla' : 'Administrador'}
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
    </div>
  );
}
