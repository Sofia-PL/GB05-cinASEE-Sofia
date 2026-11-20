import { useState, useEffect } from 'react';
import { useAuth } from '../../context/useAuth';
import { useCine } from '../../context/useCine';
import {
  obtenerCines,
  crearCine,
  actualizarCine,
  eliminarCine,
  obtenerSalasCine,
  crearSala,
  actualizarSala,
  eliminarSala,
  TIPOS_SALA,
} from '../../api/cartelera';
import { AvisoAdmin, AvisoSinToken } from './AdminAvisos';

const SALA_VACIA = {
  name: '',
  roomType: 'ESTANDAR_2D',
  rowsCount: 10,
  columnsCount: 12,
  wheelchairAccessible: true,
  basePrice: 8.5,
};

const CINE_VACIO = { name: '', address: '', city: '', phone: '' };

/** Solo los campos que guarda el Comp. 1 para un cine (CreateCinemaRequest) */
function datosCine(c) {
  return {
    name: (c?.name || '').trim(),
    address: (c?.address || '').trim(),
    city: (c?.city || '').trim(),
    phone: (c?.phone || '').trim() || null,
  };
}

export default function AdminSalasPage() {
  const { tokenUsuario } = useAuth();
  const { recargarCines } = useCine();
  const token = tokenUsuario(); // null si la sesión ha caducado

  const [cines, setCines] = useState([]);
  const [cargandoCines, setCargandoCines] = useState(true);
  const [recargaCines, setRecargaCines] = useState(0);
  const [selectedCineId, setSelectedCineId] = useState(null);

  const [recargaSalas, setRecargaSalas] = useState(0);
  /** Salas cargadas y de qué petición son (cine + recarga), para saber si están al día */
  const [salasCargadas, setSalasCargadas] = useState({ clave: null, lista: [] });
  const claveSalas = `${selectedCineId}|${recargaSalas}`;
  const salas = salasCargadas.clave === claveSalas ? salasCargadas.lista : [];
  const cargandoSalas = !!token && !!selectedCineId && salasCargadas.clave !== claveSalas;

  const [aviso, setAviso] = useState(null);
  const [guardando, setGuardando] = useState(false);

  // Pestaña activa: 'salas' o 'datos-cine'
  const [activeTab, setActiveTab] = useState('salas');

  // Modales
  const [salaModal, setSalaModal] = useState(false);
  const [salaForm, setSalaForm] = useState(SALA_VACIA);
  const [salaEnEdicion, setSalaEnEdicion] = useState(null);
  const [nuevoCineModal, setNuevoCineModal] = useState(false);
  const [nuevoCineForm, setNuevoCineForm] = useState(CINE_VACIO);

  const cineActivo = cines.find((c) => c.id === selectedCineId) || null;

  /* Cines (público) */
  useEffect(() => {
    let cancelado = false;
    obtenerCines()
      .then((lista) => {
        if (cancelado) return;
        const datos = Array.isArray(lista) ? lista : [];
        setCines(datos);
        // Se mantiene el cine elegido si sigue existiendo; si no, el primero
        setSelectedCineId((actual) => (datos.some((c) => c.id === actual) ? actual : datos[0]?.id ?? null));
      })
      .catch((err) => {
        if (!cancelado) setAviso({ tipo: 'error', texto: `No se pudieron cargar los cines: ${err.message}` });
      })
      .finally(() => {
        if (!cancelado) setCargandoCines(false);
      });
    return () => { cancelado = true; };
  }, [recargaCines]);

  /* Salas del cine elegido (requiere token) */
  useEffect(() => {
    if (!selectedCineId || !token) return undefined;
    let cancelado = false;
    const clave = `${selectedCineId}|${recargaSalas}`;
    obtenerSalasCine(selectedCineId, token)
      .then((lista) => {
        if (!cancelado) setSalasCargadas({ clave, lista: Array.isArray(lista) ? lista : [] });
      })
      .catch((err) => {
        if (!cancelado) {
          setSalasCargadas({ clave, lista: [] });
          setAviso({ tipo: 'error', texto: `No se pudieron cargar las salas: ${err.message}` });
        }
      });
    return () => { cancelado = true; };
  }, [selectedCineId, token, recargaSalas]);

  function cambiarCine(id) {
    setSelectedCineId(id);
  }

  async function handleGuardarCine(datos) {
    try {
      await actualizarCine(cineActivo.id, datosCine(datos), token);
      setAviso({ tipo: 'ok', texto: 'Datos del cine actualizados.' });
      setRecargaCines((n) => n + 1);
      recargarCines();
    } catch (err) {
      setAviso({ tipo: 'error', texto: `No se pudo guardar el cine: ${err.message}` });
    }
  }

  async function handleCrearCine(e) {
    e.preventDefault();
    setGuardando(true);
    try {
      const creado = await crearCine(datosCine(nuevoCineForm), token);
      setAviso({ tipo: 'ok', texto: `Cine "${creado?.name || nuevoCineForm.name}" creado.` });
      setNuevoCineModal(false);
      if (creado?.id) setSelectedCineId(creado.id);
      setRecargaCines((n) => n + 1);
      recargarCines();
    } catch (err) {
      setAviso({ tipo: 'error', texto: `No se pudo crear el cine: ${err.message}` });
    } finally {
      setGuardando(false);
    }
  }

  async function handleEliminarCine() {
    if (!cineActivo) return;
    if (!window.confirm(`¿Confirmas la eliminación del cine "${cineActivo.name}"?`)) return;
    try {
      await eliminarCine(cineActivo.id, token);
      setAviso({ tipo: 'ok', texto: `Cine "${cineActivo.name}" eliminado.` });
      setSelectedCineId(null);
      setRecargaCines((n) => n + 1);
      recargarCines();
    } catch (err) {
      const texto = err.status === 409
        ? `No se puede eliminar "${cineActivo.name}" porque todavía tiene salas asociadas. Elimina primero las salas del cine.`
        : `No se pudo eliminar el cine: ${err.message}`;
      setAviso({ tipo: 'error', texto });
    }
  }

  function abrirCrearSala() {
    setSalaEnEdicion(null);
    setSalaForm({ ...SALA_VACIA, name: `Sala ${salas.length + 1}` });
    setSalaModal(true);
  }

  function abrirEditarSala(sala) {
    setSalaEnEdicion(sala.id);
    setSalaForm({
      name: sala.name || '',
      roomType: sala.roomType || 'ESTANDAR_2D',
      rowsCount: sala.rowsCount || 1,
      columnsCount: sala.columnsCount || 1,
      wheelchairAccessible: !!sala.wheelchairAccessible,
      basePrice: sala.basePrice ?? 0,
    });
    setSalaModal(true);
  }

  async function handleGuardarSala(e) {
    e.preventDefault();
    setGuardando(true);
    try {
      const datos = {
        cinemaId: salas.find((sala) => sala.id === salaEnEdicion)?.cinemaId || cineActivo.id,
        name: salaForm.name.trim(),
        roomType: salaForm.roomType,
        rowsCount: parseInt(salaForm.rowsCount, 10),
        columnsCount: parseInt(salaForm.columnsCount, 10),
        wheelchairAccessible: !!salaForm.wheelchairAccessible,
        basePrice: salaForm.basePrice === '' ? null : Number(salaForm.basePrice),
      };
      if (salaEnEdicion) {
        await actualizarSala(salaEnEdicion, datos, token);
        setAviso({ tipo: 'ok', texto: `Sala "${salaForm.name}" actualizada.` });
      } else {
        await crearSala(datos, token);
        setAviso({ tipo: 'ok', texto: `Sala "${salaForm.name}" creada en ${cineActivo.name}.` });
      }
      setSalaModal(false);
      setRecargaSalas((n) => n + 1);
    } catch (err) {
      setAviso({ tipo: 'error', texto: `No se pudo ${salaEnEdicion ? 'actualizar' : 'crear'} la sala: ${err.message}` });
    } finally {
      setGuardando(false);
    }
  }

  async function handleEliminarSala(sala) {
    if (!window.confirm(`¿Confirmas la eliminación de la sala "${sala.name}"?`)) return;
    try {
      await eliminarSala(sala.id, token);
      setAviso({ tipo: 'ok', texto: `Sala "${sala.name}" eliminada.` });
      setRecargaSalas((n) => n + 1);
    } catch (err) {
      setAviso({ tipo: 'error', texto: `No se pudo eliminar la sala: ${err.message}` });
    }
  }

  const aforoTotal = salas.reduce((acc, s) => acc + (s.capacity || (s.rowsCount || 0) * (s.columnsCount || 0)), 0);
  const filas = parseInt(salaForm.rowsCount, 10) || 0;
  const columnas = parseInt(salaForm.columnsCount, 10) || 0;

  return (
    <div className="admin-page-container">
      <header className="admin-header">
        <div>
          <h1 className="admin-header-title">Gestión de Salas y Cine</h1>
          <p className="admin-header-desc">
            Salas de cada cine (tipo, filas, columnas, accesibilidad y precio base) y datos de contacto del cine.
          </p>
        </div>

        {/* Selector de cine del administrador */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
          <label className="admin-label" style={{ margin: 0, whiteSpace: 'nowrap' }}>
            Cine administrado:
          </label>
          <select
            className="admin-select"
            value={selectedCineId ?? ''}
            onChange={(e) => cambiarCine(e.target.value)}
            disabled={cines.length === 0}
            style={{ fontWeight: 700, minWidth: '240px' }}
          >
            {cines.length === 0 && <option value="">{cargandoCines ? 'Cargando…' : 'No hay cines'}</option>}
            {cines.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.city})
              </option>
            ))}
          </select>
          <button
            type="button"
            className="admin-btn admin-btn-secondary"
            onClick={() => { setNuevoCineForm(CINE_VACIO); setNuevoCineModal(true); }}
            disabled={!token}
          >
            + Nuevo cine
          </button>
        </div>
      </header>

      {!token && <AvisoSinToken />}
      <AvisoAdmin aviso={aviso} onCerrar={() => setAviso(null)} />

      {cineActivo && (
        <>
          {/* Pestañas: Salas vs Datos del Cine */}
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
              Datos de {cineActivo.name}
            </button>
          </div>

          {/* PESTAÑA 1: SALAS */}
          {activeTab === 'salas' && (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '18px', flexWrap: 'wrap', gap: '10px' }}>
                <p style={{ margin: 0, fontSize: '0.9rem', color: 'var(--text-muted)' }}>
                  Cine: <strong>{cineActivo.name}</strong>, aforo total: <strong>{aforoTotal} butacas</strong>
                </p>
                <button className="admin-btn admin-btn-primary" type="button" onClick={abrirCrearSala} disabled={!token}>
                  + Añadir Sala
                </button>
              </div>

              {!token ? null : cargandoSalas ? (
                <p style={{ color: 'var(--text-muted)' }}>Cargando salas…</p>
              ) : salas.length === 0 ? (
                <p style={{ color: 'var(--text-muted)' }}>Este cine todavía no tiene salas.</p>
              ) : (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(310px, 1fr))', gap: '20px' }}>
                  {salas.map((sala) => (
                    <article key={sala.id} className="admin-card">
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                        <span className="admin-badge admin-badge-warning">
                          {TIPOS_SALA[sala.roomType] || sala.roomType}
                        </span>
                        {sala.wheelchairAccessible && (
                          <span className="admin-badge admin-badge-success">Accesible</span>
                        )}
                      </div>

                      <h3 style={{ margin: '4px 0 6px', color: 'var(--burgundy)', fontSize: '1.2rem' }}>
                        {sala.name}
                      </h3>

                      <div style={{
                        background: 'rgba(43,18,21,0.03)',
                        border: '1px solid rgba(43,18,21,0.08)',
                        borderRadius: '4px',
                        padding: '12px 14px',
                        fontSize: '0.86rem',
                        lineHeight: 1.6,
                        margin: '10px 0 0',
                      }}>
                        <div>Matriz: <strong>{sala.rowsCount} filas × {sala.columnsCount} columnas</strong></div>
                        <div>Aforo: <strong>{sala.capacity} butacas</strong></div>
                        <div>Precio mínimo por entrada: <strong>{Number(sala.basePrice || 0).toFixed(2)} €</strong></div>
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '14px' }}>
                        <button
                          type="button"
                          className="admin-btn admin-btn-secondary admin-btn-sm"
                          onClick={() => abrirEditarSala(sala)}
                          disabled={!token}
                        >
                          Editar
                        </button>
                        <button
                          type="button"
                          className="admin-btn admin-btn-danger admin-btn-sm"
                          onClick={() => handleEliminarSala(sala)}
                          disabled={!token}
                        >
                          Eliminar
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </>
          )}

          {/* PESTAÑA 2: DATOS DEL CINE */}
          {activeTab === 'datos-cine' && (
            // key: al cambiar de cine (o recargarlo) el formulario vuelve a sus datos guardados
            <FichaCine
              key={`${cineActivo.id}-${recargaCines}`}
              cine={cineActivo}
              puedeEditar={!!token}
              onGuardar={handleGuardarCine}
              onEliminar={handleEliminarCine}
            />
          )}
        </>
      )}

      {/* Modal: nueva sala */}
      {salaModal && cineActivo && (
        <div className="admin-modal-backdrop" onClick={() => setSalaModal(false)}>
          <div className="admin-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal-header">
              <h2 className="admin-modal-title">{salaEnEdicion ? 'Editar sala' : 'Nueva Sala'} para {cineActivo.name}</h2>
              <button type="button" className="admin-btn admin-btn-secondary admin-btn-sm" onClick={() => setSalaModal(false)}>
                ✕
              </button>
            </div>

            <form onSubmit={handleGuardarSala}>
              <div className="admin-form-group">
                <label className="admin-label">Nombre de la Sala *</label>
                <input
                  type="text"
                  className="admin-input"
                  required
                  placeholder="Ej: Sala 1 - Gran Pantalla IMAX"
                  value={salaForm.name}
                  onChange={(e) => setSalaForm({ ...salaForm, name: e.target.value })}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label">Tipo de Sala *</label>
                  <select
                    className="admin-select"
                    required
                    value={salaForm.roomType}
                    onChange={(e) => setSalaForm({ ...salaForm, roomType: e.target.value })}
                  >
                    {Object.entries(TIPOS_SALA).map(([valor, etiqueta]) => (
                      <option key={valor} value={valor}>{etiqueta}</option>
                    ))}
                  </select>
                </div>

                <div className="admin-form-group">
                  <label className="admin-label">Precio Mínimo por Entrada (€)</label>
                  <input
                    type="number"
                    className="admin-input"
                    min={0}
                    step="0.05"
                    value={salaForm.basePrice}
                    onChange={(e) => setSalaForm({ ...salaForm, basePrice: e.target.value })}
                  />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label">Número de Filas *</label>
                  <input
                    type="number"
                    className="admin-input"
                    required
                    min={1}
                    max={30}
                    value={salaForm.rowsCount}
                    onChange={(e) => setSalaForm({ ...salaForm, rowsCount: e.target.value })}
                  />
                </div>

                <div className="admin-form-group">
                  <label className="admin-label">Butacas por Fila *</label>
                  <input
                    type="number"
                    className="admin-input"
                    required
                    min={1}
                    max={40}
                    value={salaForm.columnsCount}
                    onChange={(e) => setSalaForm({ ...salaForm, columnsCount: e.target.value })}
                  />
                </div>
              </div>

              <div className="admin-form-group" style={{ flexDirection: 'row', alignItems: 'center', gap: '10px' }}>
                <input
                  type="checkbox"
                  id="wheelchairAccessible"
                  checked={salaForm.wheelchairAccessible}
                  onChange={(e) => setSalaForm({ ...salaForm, wheelchairAccessible: e.target.checked })}
                  style={{ width: '18px', height: '18px', cursor: 'pointer' }}
                />
                <label htmlFor="wheelchairAccessible" style={{ cursor: 'pointer', fontSize: '0.88rem', fontWeight: 600 }}>
                  Accesible para sillas de ruedas
                </label>
              </div>

              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                Aforo resultante: {filas * columnas} butacas
              </span>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '22px', paddingTop: '16px', borderTop: '1px solid rgba(43,18,21,0.1)' }}>
                <button type="button" className="admin-btn admin-btn-secondary" onClick={() => setSalaModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="admin-btn admin-btn-primary" disabled={guardando}>
                  {guardando ? 'Guardando…' : salaEnEdicion ? 'Guardar cambios' : 'Crear Sala'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: nuevo cine */}
      {nuevoCineModal && (
        <div className="admin-modal-backdrop" onClick={() => setNuevoCineModal(false)}>
          <div className="admin-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal-header">
              <h2 className="admin-modal-title">Nuevo Cine</h2>
              <button type="button" className="admin-btn admin-btn-secondary admin-btn-sm" onClick={() => setNuevoCineModal(false)}>
                ✕
              </button>
            </div>
            <form onSubmit={handleCrearCine}>
              <CamposCine form={nuevoCineForm} onChange={setNuevoCineForm} />
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '22px', paddingTop: '16px', borderTop: '1px solid rgba(43,18,21,0.1)' }}>
                <button type="button" className="admin-btn admin-btn-secondary" onClick={() => setNuevoCineModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="admin-btn admin-btn-primary" disabled={guardando}>
                  {guardando ? 'Guardando…' : 'Crear Cine'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

/** Ficha editable del cine elegido */
function FichaCine({ cine, puedeEditar, onGuardar, onEliminar }) {
  const [form, setForm] = useState(() => datosCine(cine));
  const [guardando, setGuardando] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setGuardando(true);
    await onGuardar(form);
    setGuardando(false);
  }

  return (
    <div className="admin-panel" style={{ maxWidth: '780px' }}>
      <h2 style={{ color: 'var(--burgundy)', fontSize: '1.3rem', margin: '0 0 8px' }}>
        Ficha de {cine.name}
      </h2>

      <form onSubmit={handleSubmit}>
        <CamposCine form={form} onChange={setForm} />

        <div style={{ marginTop: '24px', display: 'flex', justifyContent: 'space-between', gap: '12px' }}>
          <button type="button" className="admin-btn admin-btn-danger" onClick={onEliminar} disabled={!puedeEditar}>
            Eliminar cine
          </button>
          <button type="submit" className="admin-btn admin-btn-primary" disabled={!puedeEditar || guardando}>
            {guardando ? 'Guardando…' : 'Guardar Información del Cine'}
          </button>
        </div>
      </form>
    </div>
  );
}

/** Campos de un cine tal como los guarda Cartelera: nombre, ciudad, dirección y teléfono */
function CamposCine({ form, onChange }) {
  return (
    <>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
        <div className="admin-form-group">
          <label className="admin-label">Nombre del Cine *</label>
          <input
            type="text"
            className="admin-input"
            required
            value={form.name}
            onChange={(e) => onChange({ ...form, name: e.target.value })}
          />
        </div>

        <div className="admin-form-group">
          <label className="admin-label">Ciudad *</label>
          <input
            type="text"
            className="admin-input"
            required
            value={form.city}
            onChange={(e) => onChange({ ...form, city: e.target.value })}
          />
        </div>
      </div>

      <div className="admin-form-group">
        <label className="admin-label">Dirección Completa *</label>
        <input
          type="text"
          className="admin-input"
          required
          value={form.address}
          onChange={(e) => onChange({ ...form, address: e.target.value })}
        />
      </div>

      <div className="admin-form-group">
        <label className="admin-label">Teléfono de Contacto</label>
        <input
          type="tel"
          className="admin-input"
          placeholder="Ej: 91 521 84 92"
          value={form.phone || ''}
          onChange={(e) => onChange({ ...form, phone: e.target.value })}
        />
      </div>
    </>
  );
}
