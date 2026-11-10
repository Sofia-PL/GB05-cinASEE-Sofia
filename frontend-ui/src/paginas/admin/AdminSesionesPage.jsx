import { useState, useEffect } from 'react';
import initialPeliculas from '../../assets/peliculas.json';
import initialCines from '../../assets/cines.json';

const LS_PELIS_KEY = 'cinasee_admin_peliculas';
const LS_SESIONES_KEY_PREFIX = 'cinasee_admin_sesiones_';
const LS_ACTIVE_CINE = 'cinasee_admin_active_cine_id';

function getPeliculas() {
  try {
    const raw = localStorage.getItem(LS_PELIS_KEY);
    if (raw) return JSON.parse(raw);
  } catch {
    // fallback
  }
  return initialPeliculas;
}

function getInitialSesiones(cineId, pelis) {
  const peli1 = pelis[0]?.title || 'Los Juegos del Hambre';
  const peli2 = pelis[1]?.title || 'Godzilla Minus Zero';
  const peli3 = pelis[2]?.title || 'La bola negra';

  return [
    { id: 101, cineId, peli: peli1, sala: 1, hora: '16:30', fecha: '2026-11-10', formato: '2D', precio: 9.50 },
    { id: 102, cineId, peli: peli1, sala: 5, hora: '19:15', fecha: '2026-11-10', formato: 'IMAX', precio: 14.00 },
    { id: 103, cineId, peli: peli1, sala: 5, hora: '22:00', fecha: '2026-11-10', formato: 'IMAX', precio: 14.00 },
    { id: 104, cineId, peli: peli2, sala: 3, hora: '18:00', fecha: '2026-11-10', formato: '3D', precio: 11.50 },
    { id: 105, cineId, peli: peli2, sala: 3, hora: '21:00', fecha: '2026-11-10', formato: '3D', precio: 11.50 },
    { id: 106, cineId, peli: peli3, sala: 2, hora: '17:30', fecha: '2026-11-10', formato: '2D', precio: 9.50 },
    { id: 107, cineId, peli: peli3, sala: 4, hora: '20:15', fecha: '2026-11-10', formato: 'VIP', precio: 15.00 },
  ];
}

function loadSesionesForCine(cineId, pelis) {
  const key = `${LS_SESIONES_KEY_PREFIX}${cineId}`;
  try {
    const raw = localStorage.getItem(key);
    if (raw) return JSON.parse(raw);
  } catch {
    // fallback
  }
  return getInitialSesiones(cineId, pelis);
}

export default function AdminSesionesPage() {
  const peliculas = getPeliculas();
  const cines = initialCines;

  const [selectedCineId, setSelectedCineId] = useState(() => {
    const saved = localStorage.getItem(LS_ACTIVE_CINE);
    return saved ? Number(saved) : cines[0].id;
  });

  const cineActivo = cines.find((c) => c.id === selectedCineId) || cines[0];
  const [sesiones, setSesiones] = useState(() => loadSesionesForCine(cineActivo.id, peliculas));

  // Película seleccionada para filtrar en el maestro-detalle
  const [selectedPeliId, setSelectedPeliId] = useState(() => peliculas[0]?.id || 1);
  const selectedPeli = peliculas.find((p) => p.id === selectedPeliId) || peliculas[0];

  const [busquedaPeli, setBusquedaPeli] = useState('');

  // Modal para crear / editar sesión
  const [modalMode, setModalMode] = useState(null); // null, 'create', 'edit'
  const [formData, setFormData] = useState({
    id: null,
    peli: selectedPeli?.title || '',
    sala: 1,
    hora: '18:00',
    fecha: new Date().toISOString().split('T')[0],
    formato: '2D',
    precio: 9.50,
  });

  // Persistir cine activo
  useEffect(() => {
    localStorage.setItem(LS_ACTIVE_CINE, String(selectedCineId));
    setSesiones(loadSesionesForCine(selectedCineId, peliculas));
  }, [selectedCineId]);

  // Persistir sesiones del cine actual
  useEffect(() => {
    const key = `${LS_SESIONES_KEY_PREFIX}${cineActivo.id}`;
    try {
      localStorage.setItem(key, JSON.stringify(sesiones));
    } catch {
      // ignore
    }
  }, [sesiones, cineActivo.id]);

  // Filtrar sesiones por la película seleccionada a la derecha
  const sesionesPelicula = sesiones.filter(
    (s) => s.peli.trim().toLowerCase() === selectedPeli?.title.trim().toLowerCase()
  );

  const peliculasFiltradas = peliculas.filter((p) =>
    p.title.toLowerCase().includes(busquedaPeli.toLowerCase())
  );

  function abrirCrearSesion() {
    setFormData({
      id: null,
      peli: selectedPeli.title,
      sala: 1,
      hora: '19:00',
      fecha: new Date().toISOString().split('T')[0],
      formato: '2D',
      precio: 9.50,
    });
    setModalMode('create');
  }

  function abrirEditarSesion(sesion) {
    setFormData({
      id: sesion.id,
      peli: sesion.peli,
      sala: sesion.sala,
      hora: sesion.hora,
      fecha: sesion.fecha || new Date().toISOString().split('T')[0],
      formato: sesion.formato,
      precio: sesion.precio,
    });
    setModalMode('edit');
  }

  function handleSubmit(e) {
    e.preventDefault();
    const precioNum = parseFloat(formData.precio) || 9.50;
    const salaNum = parseInt(formData.sala, 10) || 1;

    if (modalMode === 'create') {
      const nueva = {
        ...formData,
        id: Date.now(),
        cineId: cineActivo.id,
        precio: precioNum,
        sala: salaNum,
      };
      setSesiones([...sesiones, nueva]);
    } else if (modalMode === 'edit') {
      setSesiones(
        sesiones.map((s) =>
          s.id === formData.id
            ? { ...s, ...formData, precio: precioNum, sala: salaNum }
            : s
        )
      );
    }
    setModalMode(null);
  }

  function handleDelete(id) {
    if (window.confirm('¿Confirmas la cancelación y eliminación de este pase?')) {
      setSesiones(sesiones.filter((s) => s.id !== id));
    }
  }

  return (
    <div className="admin-page-container">
      <header className="admin-header">
        <div>
          <h1 className="admin-header-title">Programación de Sesiones por Película</h1>
          <p className="admin-header-desc">
            Organización de horarios, salas asignadas y precios de venta para cada título en este complejo.
          </p>
        </div>

        {/* Selector de cine del administrador */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <label className="admin-label" style={{ margin: 0, whiteSpace: 'nowrap' }}>
            Cine administrado:
          </label>
          <select
            className="admin-select"
            value={selectedCineId}
            onChange={(e) => setSelectedCineId(Number(e.target.value))}
            style={{ fontWeight: 700, minWidth: '240px' }}
          >
            {cines.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.city})
              </option>
            ))}
          </select>
        </div>
      </header>

      {/* Maestro-Detalle: Películas a la izquierda / Sesiones de esa película a la derecha */}
      <div className="admin-master-detail">
        {/* Columna Izquierda: Filtro y Catálogo de Películas */}
        <aside className="admin-list-sidebar">
          <div className="admin-list-sidebar-header">
            Catálogo en Cartelera ({peliculas.length})
          </div>

          <div style={{ padding: '10px 14px', borderBottom: '1px solid rgba(43,18,21,0.08)' }}>
            <input
              type="text"
              className="admin-input"
              placeholder="Filtrar por película..."
              value={busquedaPeli}
              onChange={(e) => setBusquedaPeli(e.target.value)}
              style={{ fontSize: '0.85rem', padding: '6px 10px' }}
            />
          </div>

          <div style={{ maxHeight: '600px', overflowY: 'auto' }}>
            {peliculasFiltradas.map((peli) => {
              const isSelected = peli.id === selectedPeli?.id;
              const cantSesiones = sesiones.filter(
                (s) => s.peli.trim().toLowerCase() === peli.title.trim().toLowerCase()
              ).length;

              return (
                <button
                  key={peli.id}
                  type="button"
                  className={`admin-list-item${isSelected ? ' selected' : ''}`}
                  onClick={() => setSelectedPeliId(peli.id)}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px', overflow: 'hidden' }}>
                    {peli.posterUrl && (
                      <img
                        src={peli.posterUrl}
                        alt=""
                        style={{ width: '28px', height: '40px', objectFit: 'cover', borderRadius: '2px', flexShrink: 0 }}
                      />
                    )}
                    <span style={{ fontSize: '0.9rem', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {peli.title}
                    </span>
                  </div>

                  <span className={`admin-badge ${cantSesiones > 0 ? 'admin-badge-dark' : ''}`} style={{ flexShrink: 0, marginLeft: '8px' }}>
                    {cantSesiones} {cantSesiones === 1 ? 'pase' : 'pases'}
                  </span>
                </button>
              );
            })}
          </div>
        </aside>

        {/* Columna Derecha: Todas las sesiones de la película seleccionada */}
        <section className="admin-panel" style={{ margin: 0 }}>
          {selectedPeli ? (
            <>
              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                paddingBottom: '16px',
                marginBottom: '16px',
                borderBottom: '1px solid rgba(43,18,21,0.12)',
                flexWrap: 'wrap',
                gap: '12px'
              }}>
                <div>
                  <span className="admin-badge admin-badge-warning" style={{ marginBottom: '4px' }}>
                    {selectedPeli.genre} · {selectedPeli.ageRating} · {selectedPeli.durationMinutes} min
                  </span>
                  <h2 style={{ color: 'var(--burgundy)', margin: '2px 0 0', fontSize: '1.4rem' }}>
                    Sesiones de: {selectedPeli.title}
                  </h2>
                  <p style={{ margin: '4px 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                    Programación en {cineActivo.name} ({cineActivo.city})
                  </p>
                </div>

                <button
                  type="button"
                  className="admin-btn admin-btn-primary"
                  onClick={abrirCrearSesion}
                >
                  + Añadir Sesión a esta Película
                </button>
              </div>

              {/* Listado / Tabla de Sesiones de esta Película */}
              {sesionesPelicula.length === 0 ? (
                <div style={{
                  padding: '48px 20px',
                  textAlign: 'center',
                  background: 'rgba(43,18,21,0.02)',
                  borderRadius: '4px',
                  border: '1px dashed rgba(43,18,21,0.2)'
                }}>
                  <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.95rem' }}>
                    No hay ningún horario programado para <strong>{selectedPeli.title}</strong> en {cineActivo.name}.
                  </p>
                  <button
                    type="button"
                    className="admin-btn admin-btn-secondary"
                    onClick={abrirCrearSesion}
                    style={{ marginTop: '12px' }}
                  >
                    Programar primer pase
                  </button>
                </div>
              ) : (
                <div className="admin-table-wrap">
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>Fecha</th>
                        <th>Horario</th>
                        <th>Sala</th>
                        <th>Formato</th>
                        <th>Tarifa</th>
                        <th style={{ textAlign: 'right' }}>Acciones</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sesionesPelicula.map((sesion) => (
                        <tr key={sesion.id}>
                          <td style={{ fontWeight: 600 }}>{sesion.fecha || '2026-11-10'}</td>
                          <td>
                            <strong style={{ fontSize: '1.1rem', color: 'var(--burgundy)' }}>
                              {sesion.hora}
                            </strong>
                          </td>
                          <td>Sala {sesion.sala}</td>
                          <td>
                            <span className="admin-badge admin-badge-dark">{sesion.formato}</span>
                          </td>
                          <td>
                            <strong>{sesion.precio.toFixed(2)} €</strong>
                          </td>
                          <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                            <button
                              type="button"
                              className="admin-btn admin-btn-secondary admin-btn-sm"
                              onClick={() => abrirEditarSesion(sesion)}
                              style={{ marginRight: '6px' }}
                            >
                              Editar
                            </button>
                            <button
                              type="button"
                              className="admin-btn admin-btn-danger admin-btn-sm"
                              onClick={() => handleDelete(sesion.id)}
                            >
                              Eliminar
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          ) : (
            <p style={{ color: 'var(--text-muted)' }}>Selecciona una película a la izquierda para ver y gestionar sus sesiones.</p>
          )}
        </section>
      </div>

      {/* Modal CRUD: Añadir / Editar Sesión */}
      {modalMode && (
        <div className="admin-modal-backdrop" onClick={() => setModalMode(null)}>
          <div className="admin-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal-header">
              <h2 className="admin-modal-title">
                {modalMode === 'create' ? 'Programar Nueva Sesión' : 'Modificar Horario de Sesión'}
              </h2>
              <button
                type="button"
                className="admin-btn admin-btn-secondary admin-btn-sm"
                onClick={() => setModalMode(null)}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSubmit}>
              <div className="admin-form-group">
                <label className="admin-label">Película a Proyectar</label>
                <select
                  className="admin-select"
                  value={formData.peli}
                  onChange={(e) => setFormData({ ...formData, peli: e.target.value })}
                >
                  {peliculas.map((p) => (
                    <option key={p.id} value={p.title}>{p.title}</option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label">Fecha de Proyección</label>
                  <input
                    type="date"
                    className="admin-input"
                    required
                    value={formData.fecha}
                    onChange={(e) => setFormData({ ...formData, fecha: e.target.value })}
                  />
                </div>

                <div className="admin-form-group">
                  <label className="admin-label">Hora del Pase (hh:mm)</label>
                  <input
                    type="time"
                    className="admin-input"
                    required
                    value={formData.hora}
                    onChange={(e) => setFormData({ ...formData, hora: e.target.value })}
                  />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label">Sala Asignada en {cineActivo.name}</label>
                  <select
                    className="admin-select"
                    value={formData.sala}
                    onChange={(e) => setFormData({ ...formData, sala: e.target.value })}
                  >
                    {[1, 2, 3, 4, 5, 6, 7, 8].map((s) => (
                      <option key={s} value={s}>Sala {s}</option>
                    ))}
                  </select>
                </div>

                <div className="admin-form-group">
                  <label className="admin-label">Formato de la Sesión</label>
                  <select
                    className="admin-select"
                    value={formData.formato}
                    onChange={(e) => setFormData({ ...formData, formato: e.target.value })}
                  >
                    <option value="2D">2D Estándar</option>
                    <option value="3D">3D RealD</option>
                    <option value="IMAX">IMAX Láser</option>
                    <option value="VIP">VIP Luxury</option>
                  </select>
                </div>
              </div>

              <div className="admin-form-group">
                <label className="admin-label">Precio por Entrada (€) *</label>
                <input
                  type="number"
                  step="0.50"
                  min="4"
                  max="30"
                  className="admin-input"
                  required
                  value={formData.precio}
                  onChange={(e) => setFormData({ ...formData, precio: e.target.value })}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '22px', paddingTop: '16px', borderTop: '1px solid rgba(43,18,21,0.1)' }}>
                <button
                  type="button"
                  className="admin-btn admin-btn-secondary"
                  onClick={() => setModalMode(null)}
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  className="admin-btn admin-btn-primary"
                >
                  {modalMode === 'create' ? 'Confirmar y Publicar Pase' : 'Guardar Modificación'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
