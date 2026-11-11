import { useState, useEffect } from 'react';
import initialCines from '../../assets/cines.json';
import { getSeatLayout } from '../usuario/CompraPage';

const LS_CINES_KEY = 'cinasee_admin_cines';
const LS_SALAS_KEY_PREFIX = 'cinasee_admin_salas_';
const LS_ACTIVE_CINE = 'cinasee_admin_active_cine_id';

function loadCines() {
  try {
    const raw = localStorage.getItem(LS_CINES_KEY);
    if (raw) return JSON.parse(raw);
  } catch {
    // fallback
  }
  return initialCines.map((c) => ({
    ...c,
    phone: c.phone || '91 521 84 92',
    email: c.email || `contacto.${c.city.toLowerCase()}@cinasee.es`,
    schedule: c.schedule || 'Lunes a Domingo: 11:30 a 23:45 h',
  }));
}

function loadSalasForCine(cine) {
  const key = `${LS_SALAS_KEY_PREFIX}${cine.id}`;
  try {
    const raw = localStorage.getItem(key);
    if (raw) return JSON.parse(raw);
  } catch {
    // fallback
  }

  // Generar salas iniciales
  return Array.from({ length: cine.rooms || 6 }, (_, i) => {
    const num = i + 1;
    const layout = getSeatLayout(cine.id, num);
    const total = layout.filas * layout.cols;
    const inoperativos = num === 3 ? 4 : 0;
    return {
      id: `${cine.id}-${num}`,
      num,
      nombre: `Sala ${num} - ${layout.tipo}`,
      formato: num === 5 ? 'IMAX' : num === 4 ? 'VIP' : num === 3 ? '3D' : '2D',
      filas: layout.filas,
      cols: layout.cols,
      asientosInoperativos: inoperativos,
      estado: inoperativos > 0 ? 'Mantenimiento parcial' : 'Operativa',
      descripcion: layout.descripcion,
    };
  });
}

const FORMATOS_DISPONIBLES = ['2D', '3D', 'IMAX', 'VIP'];

export default function AdminSalasPage() {
  const [cines, setCines] = useState(loadCines);
  const [selectedCineId, setSelectedCineId] = useState(() => {
    const saved = localStorage.getItem(LS_ACTIVE_CINE);
    return saved ? Number(saved) : cines[0].id;
  });

  const cineActivo = cines.find((c) => c.id === selectedCineId) || cines[0];
  const [salas, setSalas] = useState(() => loadSalasForCine(cineActivo));

  // Pestaña activa: 'salas' o 'datos-cine'
  const [activeTab, setActiveTab] = useState('salas');

  // Formulario de datos del cine
  const [cineForm, setCineForm] = useState({
    name: cineActivo.name,
    address: cineActivo.address,
    city: cineActivo.city,
    phone: cineActivo.phone,
    email: cineActivo.email,
    schedule: cineActivo.schedule,
  });

  const [cineGuardadoMsg, setCineGuardadoMsg] = useState(false);

  // Modal para crear / editar sala
  const [salaModal, setSalaModal] = useState(null); // null, 'create', o sala object
  const [salaForm, setSalaForm] = useState({
    num: 1,
    nombre: '',
    formato: '2D',
    filas: 10,
    cols: 12,
    asientosInoperativos: 0,
    estado: 'Operativa',
  });

  // Guardar ID del cine administrado
  useEffect(() => {
    localStorage.setItem(LS_ACTIVE_CINE, String(selectedCineId));
    setSalas(loadSalasForCine(cineActivo));
    setCineForm({
      name: cineActivo.name,
      address: cineActivo.address,
      city: cineActivo.city,
      phone: cineActivo.phone,
      email: cineActivo.email,
      schedule: cineActivo.schedule,
    });
  }, [selectedCineId, cineActivo]);

  // Persistir salas del cine actual
  useEffect(() => {
    const key = `${LS_SALAS_KEY_PREFIX}${cineActivo.id}`;
    try {
      localStorage.setItem(key, JSON.stringify(salas));
    } catch {
      // ignore
    }
  }, [salas, cineActivo.id]);

  // Persistir cines modificados
  useEffect(() => {
    try {
      localStorage.setItem(LS_CINES_KEY, JSON.stringify(cines));
    } catch {
      // ignore
    }
  }, [cines]);

  function handleGuardarCine(e) {
    e.preventDefault();
    const actualizados = cines.map((c) =>
      c.id === cineActivo.id ? { ...c, ...cineForm } : c
    );
    setCines(actualizados);
    setCineGuardadoMsg(true);
    setTimeout(() => setCineGuardadoMsg(false), 3000);
  }

  function abrirCrearSala() {
    const siguienteNum = salas.length > 0 ? Math.max(...salas.map((s) => s.num)) + 1 : 1;
    setSalaForm({
      num: siguienteNum,
      nombre: `Sala ${siguienteNum}`,
      formato: '2D',
      filas: 10,
      cols: 12,
      asientosInoperativos: 0,
      estado: 'Operativa',
    });
    setSalaModal('create');
  }

  function abrirEditarSala(sala) {
    setSalaForm({
      id: sala.id,
      num: sala.num,
      nombre: sala.nombre,
      formato: sala.formato,
      filas: sala.filas,
      cols: sala.cols,
      asientosInoperativos: sala.asientosInoperativos || 0,
      estado: sala.estado,
    });
    setSalaModal('edit');
  }

  function handleGuardarSala(e) {
    e.preventDefault();
    const filasNum = Math.max(1, parseInt(salaForm.filas, 10) || 8);
    const colsNum = Math.max(1, parseInt(salaForm.cols, 10) || 8);
    const total = filasNum * colsNum;
    const inop = Math.min(total, Math.max(0, parseInt(salaForm.asientosInoperativos, 10) || 0));

    if (salaModal === 'create') {
      const nueva = {
        id: `${cineActivo.id}-${Date.now()}`,
        num: parseInt(salaForm.num, 10) || salas.length + 1,
        nombre: salaForm.nombre || `Sala ${salaForm.num}`,
        formato: salaForm.formato,
        filas: filasNum,
        cols: colsNum,
        asientosInoperativos: inop,
        estado: inop === total ? 'Fuera de servicio' : inop > 0 ? 'Mantenimiento parcial' : salaForm.estado,
        descripcion: `Configuración técnica: ${filasNum} filas × ${colsNum} columnas`,
      };
      setSalas([...salas, nueva]);
    } else if (salaModal === 'edit') {
      setSalas(
        salas.map((s) =>
          s.id === salaForm.id
            ? {
                ...s,
                num: parseInt(salaForm.num, 10) || s.num,
                nombre: salaForm.nombre,
                formato: salaForm.formato,
                filas: filasNum,
                cols: colsNum,
                asientosInoperativos: inop,
                estado: inop === total ? 'Fuera de servicio' : inop > 0 ? 'Mantenimiento parcial' : salaForm.estado,
              }
            : s
        )
      );
    }
    setSalaModal(null);
  }

  function handleEliminarSala(id, num) {
    if (window.confirm(`¿Confirmas la eliminación de la Sala ${num} de este cine?`)) {
      setSalas(salas.filter((s) => s.id !== id));
    }
  }

  return (
    <div className="admin-page-container">
      <header className="admin-header">
        <div>
          <h1 className="admin-header-title">Gestión de Salas y Cine</h1>
          <p className="admin-header-desc">
            Configuración técnica de aforos, filas, columnas, estado operativo y datos de contacto del cine.
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

      {/* Pestañas: Salas vs Datos de Contacto del Cine */}
      <div className="admin-tab-bar">
        <button
          type="button"
          className={`admin-tab-btn${activeTab === 'salas' ? ' active' : ''}`}
          onClick={() => setActiveTab('salas')}
        >
          Salas y Aforos ({salas.length})
        </button>
        <button
          type="button"
          className={`admin-tab-btn${activeTab === 'datos-cine' ? ' active' : ''}`}
          onClick={() => setActiveTab('datos-cine')}
        >
          Datos de Contacto y Horarios de {cineActivo.name}
        </button>
      </div>

      {/* PESTAÑA 1: CRUD DE SALAS */}
      {activeTab === 'salas' && (
        <>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '18px' }}>
            <p style={{ margin: 0, fontSize: '0.9rem', color: 'var(--text-muted)' }}>
              Cine: <strong>{cineActivo.name}</strong> · Capacidad total activa: <strong>
                {salas.reduce((acc, s) => acc + (s.filas * s.cols - (s.asientosInoperativos || 0)), 0)} butacas
              </strong>
            </p>
            <button className="admin-btn admin-btn-primary" type="button" onClick={abrirCrearSala}>
              + Añadir Sala
            </button>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(310px, 1fr))', gap: '20px' }}>
            {salas.map((sala) => {
              const total = sala.filas * sala.cols;
              const inop = sala.asientosInoperativos || 0;
              const operativas = total - inop;

              return (
                <article key={sala.id} className="admin-card">
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                    <div>
                      <span className="admin-badge admin-badge-dark" style={{ marginRight: '6px' }}>
                        Sala {sala.num}
                      </span>
                      <span className="admin-badge admin-badge-warning">{sala.formato}</span>
                    </div>

                    <span className={`admin-badge ${inop > 0 ? 'admin-badge-warning' : 'admin-badge-success'}`}>
                      {sala.estado}
                    </span>
                  </div>

                  <h3 style={{ margin: '4px 0 6px', color: 'var(--burgundy)', fontSize: '1.2rem' }}>
                    {sala.nombre}
                  </h3>

                  <div style={{
                    background: 'rgba(43,18,21,0.03)',
                    border: '1px solid rgba(43,18,21,0.08)',
                    borderRadius: '4px',
                    padding: '12px 14px',
                    fontSize: '0.86rem',
                    lineHeight: 1.6,
                    margin: '10px 0 16px'
                  }}>
                    <div>Matriz: <strong>{sala.filas} filas × {sala.cols} columnas</strong></div>
                    <div>Aforo total: <strong>{total} butacas</strong></div>
                    <div>
                      Operativas: <strong style={{ color: '#155724' }}>{operativas}</strong>
                      {inop > 0 && (
                        <span style={{ color: '#856404', marginLeft: '6px' }}>
                          ({inop} averiadas / fuera de servicio)
                        </span>
                      )}
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: '8px', marginTop: 'auto', paddingTop: '10px', borderTop: '1px solid rgba(43,18,21,0.08)' }}>
                    <button
                      type="button"
                      className="admin-btn admin-btn-secondary admin-btn-sm"
                      style={{ flex: 1 }}
                      onClick={() => abrirEditarSala(sala)}
                    >
                      Editar Dimensiones
                    </button>
                    <button
                      type="button"
                      className="admin-btn admin-btn-danger admin-btn-sm"
                      onClick={() => handleEliminarSala(sala.id, sala.num)}
                    >
                      Eliminar
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        </>
      )}

      {/* PESTAÑA 2: GESTIÓN DE DATOS DE CONTACTO Y HORARIOS DEL CINE */}
      {activeTab === 'datos-cine' && (
        <div className="admin-panel" style={{ maxWidth: '780px' }}>
          <h2 style={{ color: 'var(--burgundy)', fontSize: '1.3rem', margin: '0 0 8px' }}>
            Ficha Oficial de {cineActivo.name}
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.88rem', margin: '0 0 20px' }}>
            Modifica los canales de atención al público, teléfonos, correo y horario de apertura del cine.
          </p>

          {cineGuardadoMsg && (
            <div style={{
              background: 'rgba(40,167,69,0.12)',
              border: '1px solid rgba(40,167,69,0.3)',
              color: '#155724',
              padding: '10px 16px',
              borderRadius: '4px',
              marginBottom: '18px',
              fontSize: '0.88rem',
              fontWeight: 600
            }}>
              Datos del cine actualizados y persistidos con éxito.
            </div>
          )}

          <form onSubmit={handleGuardarCine}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
              <div className="admin-form-group">
                <label className="admin-label">Nombre del Cine</label>
                <input
                  type="text"
                  className="admin-input"
                  required
                  value={cineForm.name}
                  onChange={(e) => setCineForm({ ...cineForm, name: e.target.value })}
                />
              </div>

              <div className="admin-form-group">
                <label className="admin-label">Ciudad / Provincia</label>
                <input
                  type="text"
                  className="admin-input"
                  required
                  value={cineForm.city}
                  onChange={(e) => setCineForm({ ...cineForm, city: e.target.value })}
                />
              </div>
            </div>

            <div className="admin-form-group">
              <label className="admin-label">Dirección Completa</label>
              <input
                type="text"
                className="admin-input"
                required
                value={cineForm.address}
                onChange={(e) => setCineForm({ ...cineForm, address: e.target.value })}
              />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
              <div className="admin-form-group">
                <label className="admin-label">Teléfono de Contacto y Taquilla *</label>
                <input
                  type="tel"
                  className="admin-input"
                  required
                  placeholder="Ej: 91 521 84 92"
                  value={cineForm.phone}
                  onChange={(e) => setCineForm({ ...cineForm, phone: e.target.value })}
                />
              </div>

              <div className="admin-form-group">
                <label className="admin-label">Correo Electrónico de Atención *</label>
                <input
                  type="email"
                  className="admin-input"
                  required
                  placeholder="contacto@cinasee.es"
                  value={cineForm.email}
                  onChange={(e) => setCineForm({ ...cineForm, email: e.target.value })}
                />
              </div>
            </div>

            <div className="admin-form-group">
              <label className="admin-label">Horario de Apertura y Proyecciones *</label>
              <input
                type="text"
                className="admin-input"
                required
                placeholder="Lunes a Domingo: 11:30 a 23:45 h"
                value={cineForm.schedule}
                onChange={(e) => setCineForm({ ...cineForm, schedule: e.target.value })}
              />
            </div>

            <div style={{ marginTop: '24px', display: 'flex', justifyContent: 'flex-end' }}>
              <button type="submit" className="admin-btn admin-btn-primary">
                Guardar Información del Cine
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Modal CRUD: Añadir / Modificar Sala */}
      {salaModal && (
        <div className="admin-modal-backdrop" onClick={() => setSalaModal(null)}>
          <div className="admin-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal-header">
              <h2 className="admin-modal-title">
                {salaModal === 'create' ? `Nueva Sala para ${cineActivo.name}` : `Modificar ${salaForm.nombre}`}
              </h2>
              <button
                type="button"
                className="admin-btn admin-btn-secondary admin-btn-sm"
                onClick={() => setSalaModal(null)}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleGuardarSala}>
              <div style={{ display: 'grid', gridTemplateColumns: '120px 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label">Número *</label>
                  <input
                    type="number"
                    className="admin-input"
                    required
                    min={1}
                    value={salaForm.num}
                    onChange={(e) => setSalaForm({ ...salaForm, num: e.target.value })}
                  />
                </div>

                <div className="admin-form-group">
                  <label className="admin-label">Denominación de la Sala *</label>
                  <input
                    type="text"
                    className="admin-input"
                    required
                    placeholder="Ej: Sala 1 - Gran Pantalla IMAX"
                    value={salaForm.nombre}
                    onChange={(e) => setSalaForm({ ...salaForm, nombre: e.target.value })}
                  />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label">Formato de Proyección</label>
                  <select
                    className="admin-select"
                    value={salaForm.formato}
                    onChange={(e) => setSalaForm({ ...salaForm, formato: e.target.value })}
                  >
                    {FORMATOS_DISPONIBLES.map((f) => (
                      <option key={f} value={f}>{f}</option>
                    ))}
                  </select>
                </div>

                <div className="admin-form-group">
                  <label className="admin-label">Estado de la Sala</label>
                  <select
                    className="admin-select"
                    value={salaForm.estado}
                    onChange={(e) => setSalaForm({ ...salaForm, estado: e.target.value })}
                  >
                    <option value="Operativa">Operativa</option>
                    <option value="Mantenimiento parcial">Mantenimiento parcial</option>
                    <option value="En reformas">En reformas</option>
                    <option value="Fuera de servicio">Fuera de servicio</option>
                  </select>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label">Número de Filas *</label>
                  <input
                    type="number"
                    className="admin-input"
                    required
                    min={3}
                    max={30}
                    value={salaForm.filas}
                    onChange={(e) => setSalaForm({ ...salaForm, filas: e.target.value })}
                  />
                </div>

                <div className="admin-form-group">
                  <label className="admin-label">Número de Columnas (Butacas por fila) *</label>
                  <input
                    type="number"
                    className="admin-input"
                    required
                    min={4}
                    max={40}
                    value={salaForm.cols}
                    onChange={(e) => setSalaForm({ ...salaForm, cols: e.target.value })}
                  />
                </div>
              </div>

              <div className="admin-form-group">
                <label className="admin-label">
                  Butacas NO operativas (averiadas o bloqueadas por mantenimiento)
                </label>
                <input
                  type="number"
                  className="admin-input"
                  min={0}
                  max={salaForm.filas * salaForm.cols}
                  value={salaForm.asientosInoperativos}
                  onChange={(e) => setSalaForm({ ...salaForm, asientosInoperativos: e.target.value })}
                />
                <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '3px' }}>
                  Aforo total: {salaForm.filas * salaForm.cols} butacas · Butacas operativas para venta: {Math.max(0, (salaForm.filas * salaForm.cols) - (parseInt(salaForm.asientosInoperativos, 10) || 0))}
                </span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '22px', paddingTop: '16px', borderTop: '1px solid rgba(43,18,21,0.1)' }}>
                <button
                  type="button"
                  className="admin-btn admin-btn-secondary"
                  onClick={() => setSalaModal(null)}
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  className="admin-btn admin-btn-primary"
                >
                  {salaModal === 'create' ? 'Crear Sala' : 'Guardar Cambios'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
