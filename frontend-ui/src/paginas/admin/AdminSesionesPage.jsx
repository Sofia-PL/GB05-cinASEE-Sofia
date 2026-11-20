import { useState, useEffect } from 'react';
import { useAuth } from '../../context/useAuth';
import {
  obtenerPeliculas,
  obtenerCines,
  obtenerSalasCine,
  obtenerTodasSesiones,
  crearSesion,
  crearSesionesLote,
  actualizarSesion,
  fechaIso,
  ESTADOS_SESION,
  TIPOS_SALA,
} from '../../api/cartelera';
import { AvisoAdmin, AvisoSinToken } from './AdminAvisos';
import CampoFecha from '../../components/CampoFecha';
import CampoHora from '../../components/CampoHora';
import { fechaEs } from '../../utils/fechas';

/** "2026-11-10T19:00:00" -> { fecha: "2026-11-10", hora: "19:00" } */
function partirFechaHora(startTime) {
  const s = String(startTime || '');
  return { fecha: s.slice(0, 10), hora: s.slice(11, 16) };
}

/** Fecha y hora locales del formulario -> LocalDateTime que espera Cartelera */
function unirFechaHora(fecha, hora) {
  return `${fecha}T${hora}:00`;
}

const COLOR_ESTADO = {
  PROGRAMADA: 'admin-badge-warning',
  A_LA_VENTA: 'admin-badge-success',
  EN_CURSO: 'admin-badge-dark',
  FINALIZADA: '',
  CANCELADA: 'admin-badge-dark',
};

const DIAS_SEMANA = [
  { value: 'MONDAY', label: 'Lunes' },
  { value: 'TUESDAY', label: 'Martes' },
  { value: 'WEDNESDAY', label: 'Miércoles' },
  { value: 'THURSDAY', label: 'Jueves' },
  { value: 'FRIDAY', label: 'Viernes' },
  { value: 'SATURDAY', label: 'Sábado' },
  { value: 'SUNDAY', label: 'Domingo' },
];

export default function AdminSesionesPage() {
  const { tokenUsuario } = useAuth();
  const token = tokenUsuario(); // null si la sesión ha caducado

  const [peliculas, setPeliculas] = useState([]);
  const [cines, setCines] = useState([]);
  const [sesiones, setSesiones] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [recarga, setRecarga] = useState(0);

  const [selectedCineId, setSelectedCineId] = useState(null);
  const [salas, setSalas] = useState([]);

  const [selectedPeliId, setSelectedPeliId] = useState(null);
  const [busquedaPeli, setBusquedaPeli] = useState('');

  const [aviso, setAviso] = useState(null);
  const [guardando, setGuardando] = useState(false);

  // Modal: null, 'create' o 'edit'
  const [modalMode, setModalMode] = useState(null);
  const [formData, setFormData] = useState(null);
  const [batchModal, setBatchModal] = useState(false);
  const [batchForm, setBatchForm] = useState(null);
  const [batchGuardando, setBatchGuardando] = useState(false);
  const [batchResult, setBatchResult] = useState(null);

  /* Películas, cines y sesiones (todo público en Cartelera) */
  useEffect(() => {
    let cancelado = false;
    Promise.all([obtenerPeliculas(), obtenerCines(), obtenerTodasSesiones()])
      .then(([pelis, listaCines, listaSesiones]) => {
        if (cancelado) return;
        const p = Array.isArray(pelis) ? pelis : [];
        const c = Array.isArray(listaCines) ? listaCines : [];
        setPeliculas(p);
        setCines(c);
        setSesiones(Array.isArray(listaSesiones) ? listaSesiones : []);
        setSelectedPeliId((actual) => (p.some((x) => x.id === actual) ? actual : p[0]?.id ?? null));
        setSelectedCineId((actual) => (c.some((x) => x.id === actual) ? actual : c[0]?.id ?? null));
      })
      .catch((err) => {
        if (!cancelado) setAviso({ tipo: 'error', texto: `No se pudo cargar la programación: ${err.message}` });
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });
    return () => { cancelado = true; };
  }, [recarga]);

  /* Salas del cine elegido (requiere token) */
  useEffect(() => {
    if (!selectedCineId || !token) return undefined;
    let cancelado = false;
    obtenerSalasCine(selectedCineId, token)
      .then((lista) => {
        if (!cancelado) setSalas(Array.isArray(lista) ? lista : []);
      })
      .catch((err) => {
        if (!cancelado) setAviso({ tipo: 'error', texto: `No se pudieron cargar las salas: ${err.message}` });
      });
    return () => { cancelado = true; };
  }, [selectedCineId, token]);

  const cineActivo = cines.find((c) => c.id === selectedCineId) || null;
  const selectedPeli = peliculas.find((p) => p.id === selectedPeliId) || null;
  const salaPorId = Object.fromEntries(salas.map((s) => [s.id, s]));
  const salaLote = salas.find((s) => s.id === batchForm?.roomId) || null;
  const sesionPorId = new Map([
    ...sesiones,
    ...(batchResult?.created || []),
  ].map((sesion) => [sesion.id, sesion]));

  // Sesiones del cine elegido (sus salas). Sin token no se conocen las salas: se muestran todas.
  const sesionesCine = token ? sesiones.filter((s) => salaPorId[s.roomId]) : sesiones;
  const sesionesPelicula = sesionesCine
    .filter((s) => s.movieId === selectedPeliId)
    .sort((a, b) => String(a.startTime).localeCompare(String(b.startTime)));

  const peliculasFiltradas = peliculas.filter((p) =>
    (p.title || '').toLowerCase().includes(busquedaPeli.toLowerCase())
  );

  function cambiarCine(id) {
    setSelectedCineId(id);
    setSalas([]);
  }

  function abrirCrearSesion() {
    const primera = salas[0];
    setFormData({
      id: null,
      movieId: selectedPeliId,
      roomId: primera?.id || '',
      fecha: fechaIso(new Date()),
      hora: '19:00',
      ticketPrice: primera?.basePrice ?? 9.5,
    });
    setModalMode('create');
  }

  function abrirCrearLote() {
    const primera = salas[0];
    const fechaFin = new Date();
    fechaFin.setDate(fechaFin.getDate() + 30);
    setBatchForm({
      movieId: selectedPeliId,
      roomId: primera?.id || '',
      daysOfWeek: [],
      startDate: fechaIso(new Date()),
      endDate: fechaIso(fechaFin),
      startTime: '19:00',
      ticketPrice: primera?.basePrice ?? 9.5,
      // Si se marca, las sesiones creadas se pasan a A_LA_VENTA (PATCH) al terminar el lote
      aLaVenta: false,
    });
    setBatchResult(null);
    setBatchModal(true);
  }

  function cambiarDiaLote(dia) {
    setBatchForm((actual) => ({
      ...actual,
      daysOfWeek: actual.daysOfWeek.includes(dia)
        ? actual.daysOfWeek.filter((seleccionado) => seleccionado !== dia)
        : [...actual.daysOfWeek, dia],
    }));
  }

  async function handleSubmitLote(e) {
    e.preventDefault();
    if (batchForm.daysOfWeek.length === 0) {
      setAviso({ tipo: 'error', texto: 'Selecciona al menos un día de la semana para programar el lote.' });
      return;
    }
    if (batchForm.endDate < batchForm.startDate) {
      setAviso({ tipo: 'error', texto: 'La fecha final no puede ser anterior a la fecha inicial.' });
      return;
    }

    setBatchGuardando(true);
    try {
      const resultado = await crearSesionesLote({
        movieId: batchForm.movieId,
        roomId: batchForm.roomId,
        daysOfWeek: batchForm.daysOfWeek,
        startDate: batchForm.startDate,
        endDate: batchForm.endDate,
        startTime: `${batchForm.startTime}:00`,
        ticketPrice: Number(batchForm.ticketPrice),
      }, token);

      // Cartelera las crea PROGRAMADAS: si se pidió, se ponen a la venta con el PATCH de siempre
      let sinPonerALaVenta = 0;
      if (batchForm.aLaVenta && resultado.created.length > 0) {
        const cambios = await Promise.allSettled(
          resultado.created.map((sesion) => actualizarSesion(sesion.id, { status: 'A_LA_VENTA' }, token))
        );
        sinPonerALaVenta = cambios.filter((c) => c.status === 'rejected').length;
      }

      setBatchResult(resultado);
      setBatchModal(false);
      setSelectedPeliId(batchForm.movieId);
      setRecarga((n) => n + 1);
      const totalCreadas = resultado.created.length;
      const totalConflictos = resultado.conflicts.length;
      const textoCreadas = batchForm.aLaVenta ? 'sesiones creadas y puestas a la venta' : 'sesiones creadas';
      const avisoVenta = sinPonerALaVenta > 0
        ? ` ${sinPonerALaVenta} no se pudieron poner a la venta y siguen como «Programadas».`
        : '';
      setAviso({
        tipo: totalConflictos > 0 || sinPonerALaVenta > 0 ? 'info' : 'ok',
        texto: totalCreadas === 0 && totalConflictos === 0
          ? 'El rango no incluye ninguno de los días seleccionados.'
          : `Lote completado: ${totalCreadas} ${textoCreadas} y ${totalConflictos} con solapamiento.${avisoVenta}`,
      });
    } catch (err) {
      setAviso({ tipo: 'error', texto: `No se pudo programar el lote: ${err.message}` });
    } finally {
      setBatchGuardando(false);
    }
  }

  function abrirEditarSesion(sesion) {
    const { fecha, hora } = partirFechaHora(sesion.startTime);
    setFormData({
      id: sesion.id,
      movieId: sesion.movieId,
      roomId: sesion.roomId,
      fecha,
      hora,
      ticketPrice: sesion.ticketPrice,
      original: sesion,
    });
    setModalMode('edit');
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setGuardando(true);
    try {
      const startTime = unirFechaHora(formData.fecha, formData.hora);
      if (modalMode === 'create') {
        await crearSesion({
          movieId: formData.movieId,
          roomId: formData.roomId,
          startTime,
          ticketPrice: Number(formData.ticketPrice),
        }, token);
        setAviso({
          tipo: 'ok',
          texto: 'Sesión creada como PROGRAMADA. Ponla "A la venta" para que se puedan comprar entradas.',
        });
      } else {
        // PATCH admite sala, hora, precio y estado: se envía únicamente lo que ha cambiado
        const cambios = {};
        if (formData.roomId !== formData.original.roomId) cambios.roomId = formData.roomId;
        const original = partirFechaHora(formData.original.startTime);
        if (startTime !== unirFechaHora(original.fecha, original.hora)) cambios.startTime = startTime;
        if (Number(formData.ticketPrice) !== Number(formData.original.ticketPrice)) {
          cambios.ticketPrice = Number(formData.ticketPrice);
        }
        if (Object.keys(cambios).length > 0) {
          await actualizarSesion(formData.id, cambios, token);
          setAviso({ tipo: 'ok', texto: 'Sesión modificada.' });
        }
      }
      setModalMode(null);
      setRecarga((n) => n + 1);
    } catch (err) {
      const texto = err.status === 409
        ? 'La sala ya tiene otra sesión que se solapa con ese horario.'
        : err.message;
      setAviso({ tipo: 'error', texto: `No se pudo guardar la sesión: ${texto}` });
    } finally {
      setGuardando(false);
    }
  }

  async function cambiarEstado(sesion, status) {
    if (status === sesion.status) return;
    if (status === 'CANCELADA' && !window.confirm('¿Confirmas la cancelación de este pase?')) return;
    try {
      await actualizarSesion(sesion.id, { status }, token);
      setAviso({ tipo: 'ok', texto: `Sesión marcada como "${ESTADOS_SESION[status] || status}".` });
      setRecarga((n) => n + 1);
    } catch (err) {
      setAviso({ tipo: 'error', texto: `No se pudo cambiar el estado: ${err.message}` });
    }
  }

  return (
    <div className="admin-page-container">
      <header className="admin-header">
        <div>
          <h1 className="admin-header-title">Programación de Sesiones por Película</h1>
          <p className="admin-header-desc">
            Horarios, salas, precio y estado de venta de cada pase. Solo se venden las sesiones «A la venta».
          </p>
        </div>

        {/* Selector de cine del administrador */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
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
            {cines.length === 0 && <option value="">{cargando ? 'Cargando…' : 'No hay cines'}</option>}
            {cines.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.city})
              </option>
            ))}
          </select>
        </div>
      </header>

      {!token && <AvisoSinToken />}
      <AvisoAdmin aviso={aviso} onCerrar={() => setAviso(null)} />

      {batchResult && (
        <section className="admin-panel" role="status" style={{ marginBottom: '18px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '12px' }}>
            <div>
              <h2 style={{ color: 'var(--burgundy)', margin: '0 0 6px', fontSize: '1.2rem' }}>
                Resultado de la programación por lote
              </h2>
              <p style={{ margin: 0, color: 'var(--text-muted)' }}>
                {batchResult.created.length} sesiones creadas y {batchResult.conflicts.length} fechas con solapamiento.
              </p>
            </div>
            <button
              type="button"
              className="admin-btn admin-btn-secondary admin-btn-sm"
              aria-label="Cerrar resultado del lote"
              onClick={() => setBatchResult(null)}
            >
              ✕
            </button>
          </div>

          {batchResult.created.length > 0 && (
            <details style={{ marginTop: '14px' }}>
              <summary style={{ cursor: 'pointer', fontWeight: 700 }}>
                Sesiones creadas ({batchResult.created.length})
              </summary>
              <ul style={{ margin: '8px 0 0', paddingLeft: '22px' }}>
                {batchResult.created.map((sesion) => {
                  const inicio = partirFechaHora(sesion.startTime);
                  return <li key={sesion.id}>{fechaEs(inicio.fecha)}, {inicio.hora}</li>;
                })}
              </ul>
            </details>
          )}

          {batchResult.conflicts.length > 0 && (
            <div style={{ marginTop: '14px' }}>
              <strong>Fechas no creadas por solapamiento</strong>
              <ul style={{ margin: '8px 0 0', paddingLeft: '22px' }}>
                {batchResult.conflicts.map((conflicto) => {
                  const inicio = partirFechaHora(conflicto.startTime);
                  const sesionConflicto = sesionPorId.get(conflicto.conflictingSessionId);
                  const tituloConflicto = peliculas.find((pelicula) => pelicula.id === sesionConflicto?.movieId)?.title
                    || 'otra película';
                  return (
                    <li key={`${conflicto.startTime}-${conflicto.conflictingSessionId}`}>
                      {fechaEs(inicio.fecha)}, {inicio.hora} (conflicto con «{tituloConflicto}»)
                    </li>
                  );
                })}
              </ul>
            </div>
          )}
        </section>
      )}

      {/* Maestro-Detalle: Películas a la izquierda / Sesiones de esa película a la derecha */}
      <div className="admin-master-detail">
        <aside className="admin-list-sidebar">
          <div className="admin-list-sidebar-header">
            Catálogo ({peliculas.length})
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
              const isSelected = peli.id === selectedPeliId;
              const cantSesiones = sesionesCine.filter((s) => s.movieId === peli.id).length;

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

        <section className="admin-panel" style={{ margin: 0 }}>
          {cargando ? (
            <p style={{ color: 'var(--text-muted)' }}>Cargando programación…</p>
          ) : selectedPeli ? (
            <>
              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                paddingBottom: '16px',
                marginBottom: '16px',
                borderBottom: '1px solid rgba(43,18,21,0.12)',
                flexWrap: 'wrap',
                gap: '12px',
              }}>
                <div>
                  <span className="admin-badge admin-badge-warning" style={{ marginBottom: '4px' }}>
                    {selectedPeli.genre}, {selectedPeli.ageRating}, {selectedPeli.durationMinutes} min
                  </span>
                  <h2 style={{ color: 'var(--burgundy)', margin: '2px 0 0', fontSize: '1.4rem' }}>
                    Sesiones de: {selectedPeli.title}
                  </h2>
                  {cineActivo && (
                    <p style={{ margin: '4px 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                      Programación en {cineActivo.name} ({cineActivo.city})
                    </p>
                  )}
                </div>

                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  <button
                    type="button"
                    className="admin-btn admin-btn-secondary"
                    onClick={abrirCrearLote}
                    disabled={!token || salas.length === 0}
                    title={token && salas.length === 0 ? 'Este cine no tiene salas' : undefined}
                  >
                    Programar lote
                  </button>
                  <button
                    type="button"
                    className="admin-btn admin-btn-primary"
                    onClick={abrirCrearSesion}
                    disabled={!token || salas.length === 0}
                    title={token && salas.length === 0 ? 'Este cine no tiene salas' : undefined}
                  >
                    + Añadir Sesión a esta Película
                  </button>
                </div>
              </div>

              {sesionesPelicula.length === 0 ? (
                <div style={{
                  padding: '48px 20px',
                  textAlign: 'center',
                  background: 'rgba(43,18,21,0.02)',
                  borderRadius: '4px',
                  border: '1px dashed rgba(43,18,21,0.2)',
                }}>
                  <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.95rem' }}>
                    No hay ningún horario programado para <strong>{selectedPeli.title}</strong>
                    {cineActivo ? ` en ${cineActivo.name}` : ''}.
                  </p>
                </div>
              ) : (
                <div className="admin-table-wrap">
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>Fecha</th>
                        <th>Horario</th>
                        <th>Sala</th>
                        <th>Tarifa</th>
                        <th>Estado</th>
                        <th style={{ textAlign: 'right' }}>Acciones</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sesionesPelicula.map((sesion) => {
                        const inicio = partirFechaHora(sesion.startTime);
                        const fin = partirFechaHora(sesion.endTime);
                        const sala = salaPorId[sesion.roomId];
                        return (
                          <tr key={sesion.id}>
                            <td style={{ fontWeight: 600 }}>{fechaEs(inicio.fecha)}</td>
                            <td>
                              <strong style={{ fontSize: '1.1rem', color: 'var(--burgundy)' }}>{inicio.hora}</strong>
                              {fin.hora && (
                                <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}> – {fin.hora}</span>
                              )}
                            </td>
                            <td>
                              {sala ? (
                                <>
                                  {sala.name}{' '}
                                  <span className="admin-badge admin-badge-dark">{TIPOS_SALA[sala.roomType] || sala.roomType}</span>
                                </>
                              ) : (
                                <span style={{ color: 'var(--text-muted)' }}>{sesion.roomId}</span>
                              )}
                            </td>
                            <td>
                              <strong>{Number(sesion.ticketPrice || 0).toFixed(2)} €</strong>
                            </td>
                            <td>
                              {token ? (
                                <select
                                  className="admin-select"
                                  value={sesion.status || 'PROGRAMADA'}
                                  onChange={(e) => cambiarEstado(sesion, e.target.value)}
                                  style={{ fontSize: '0.82rem', padding: '4px 8px' }}
                                >
                                  {Object.entries(ESTADOS_SESION).map(([valor, etiqueta]) => (
                                    <option key={valor} value={valor}>{etiqueta}</option>
                                  ))}
                                </select>
                              ) : (
                                <span className={`admin-badge ${COLOR_ESTADO[sesion.status] || ''}`}>
                                  {ESTADOS_SESION[sesion.status] || sesion.status}
                                </span>
                              )}
                            </td>
                            <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                              <button
                                type="button"
                                className="admin-btn admin-btn-secondary admin-btn-sm"
                                onClick={() => abrirEditarSesion(sesion)}
                                disabled={!token || sesion.status === 'CANCELADA'}
                                style={{ marginRight: '6px' }}
                              >
                                Editar
                              </button>
                              <button
                                type="button"
                                className="admin-btn admin-btn-danger admin-btn-sm"
                                onClick={() => cambiarEstado(sesion, 'CANCELADA')}
                                disabled={!token || sesion.status === 'CANCELADA'}
                              >
                                Cancelar
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          ) : (
            <p style={{ color: 'var(--text-muted)' }}>No hay películas en el catálogo.</p>
          )}
        </section>
      </div>

      {/* Modal: crear / editar sesión */}
      {modalMode && formData && (
        <div className="admin-modal-backdrop" onClick={() => setModalMode(null)}>
          <div className="admin-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal-header">
              <h2 className="admin-modal-title">
                {modalMode === 'create' ? 'Programar Nueva Sesión' : 'Modificar Horario de Sesión'}
              </h2>
              <button type="button" className="admin-btn admin-btn-secondary admin-btn-sm" onClick={() => setModalMode(null)}>
                ✕
              </button>
            </div>

            <form onSubmit={handleSubmit}>
              <div className="admin-form-group">
                <label className="admin-label">Película a Proyectar *</label>
                <select
                  className="admin-select"
                  required
                  value={formData.movieId || ''}
                  disabled={modalMode === 'edit'}
                  onChange={(e) => setFormData({ ...formData, movieId: e.target.value })}
                >
                  {peliculas.map((p) => (
                    <option key={p.id} value={p.id}>{p.title}</option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label" htmlFor="sesion-fecha">Fecha de Proyección *</label>
                  <CampoFecha
                    id="sesion-fecha"
                    required
                    value={formData.fecha}
                    onChange={(fecha) => setFormData({ ...formData, fecha })}
                  />
                </div>

                <div className="admin-form-group">
                  <label className="admin-label" htmlFor="sesion-hora">Hora del Pase *</label>
                  <CampoHora
                    id="sesion-hora"
                    required
                    value={formData.hora}
                    onChange={(hora) => setFormData({ ...formData, hora })}
                  />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label">Sala en {cineActivo?.name} *</label>
                  <select
                    className="admin-select"
                    required
                    value={formData.roomId}
                    onChange={(e) => {
                      const sala = salaPorId[e.target.value];
                      const minimo = Number(sala?.basePrice || 0);
                      setFormData({
                        ...formData,
                        roomId: e.target.value,
                        // Al crear se propone el precio mínimo de la sala; al editar solo se sube si queda por debajo
                        ticketPrice: modalMode === 'create' || Number(formData.ticketPrice) < minimo ? minimo : formData.ticketPrice,
                      });
                    }}
                  >
                    {salas.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name} ({TIPOS_SALA[s.roomType] || s.roomType})
                      </option>
                    ))}
                  </select>
                </div>

                <div className="admin-form-group">
                  <label className="admin-label">Precio por Entrada (€) *</label>
                  <input
                    type="number"
                    step="0.05"
                    min={Number(salaPorId[formData.roomId]?.basePrice || 0)}
                    className="admin-input"
                    required
                    value={formData.ticketPrice}
                    onChange={(e) => setFormData({ ...formData, ticketPrice: e.target.value })}
                  />
                  <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '3px' }}>
                    Mínimo de la sala: {Number(salaPorId[formData.roomId]?.basePrice || 0).toFixed(2)} €
                  </span>
                </div>
              </div>

              <div className="admin-form-group">
                <label className="admin-label">Formato de la Sesión</label>
                <input
                  type="text"
                  className="admin-input"
                  disabled
                  value={TIPOS_SALA[salaPorId[formData.roomId]?.roomType] || salaPorId[formData.roomId]?.roomType || '-'}
                />
                <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '3px' }}>
                  El formato lo da el tipo de la sala elegida.
                </span>
              </div>

              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', margin: '4px 0 0' }}>
                {modalMode === 'create'
                  ? 'La hora de fin la calcula Cartelera (duración de la película + 20 min de limpieza). La sesión se crea como «Programada».'
                  : 'La película de una sesión ya creada no se puede cambiar. Un cambio de precio solo afecta a las reservas nuevas.'}
              </p>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '22px', paddingTop: '16px', borderTop: '1px solid rgba(43,18,21,0.1)' }}>
                <button type="button" className="admin-btn admin-btn-secondary" onClick={() => setModalMode(null)}>
                  Cancelar
                </button>
                <button type="submit" className="admin-btn admin-btn-primary" disabled={guardando}>
                  {guardando ? 'Guardando…' : modalMode === 'create' ? 'Programar Sesión' : 'Guardar Modificación'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {batchModal && batchForm && (
        <div
          className="admin-modal-backdrop"
          onClick={() => { if (!batchGuardando) setBatchModal(false); }}
        >
          <div className="admin-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal-header">
              <h2 className="admin-modal-title">Programar sesiones recurrentes</h2>
              <button
                type="button"
                className="admin-btn admin-btn-secondary admin-btn-sm"
                onClick={() => setBatchModal(false)}
                disabled={batchGuardando}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSubmitLote}>
              <div className="admin-form-group">
                <label className="admin-label">Película</label>
                <input
                  type="text"
                  className="admin-input"
                  value={selectedPeli?.title || ''}
                  disabled
                />
              </div>

              <div className="admin-form-group">
                <label className="admin-label" htmlFor="batch-room">Sala en {cineActivo?.name} *</label>
                <select
                  id="batch-room"
                  className="admin-select"
                  required
                  value={batchForm.roomId}
                  onChange={(e) => {
                    const sala = salaPorId[e.target.value];
                    const minimo = Number(sala?.basePrice || 0);
                    setBatchForm({
                      ...batchForm,
                      roomId: e.target.value,
                      ticketPrice: Number(batchForm.ticketPrice) < minimo ? minimo : batchForm.ticketPrice,
                    });
                  }}
                >
                  {salas.map((sala) => (
                    <option key={sala.id} value={sala.id}>
                      {sala.name} ({TIPOS_SALA[sala.roomType] || sala.roomType})
                    </option>
                  ))}
                </select>
              </div>

              <fieldset style={{ border: 0, padding: 0, margin: '0 0 16px' }}>
                <legend className="admin-label">Días de la semana *</legend>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(115px, 1fr))', gap: '8px' }}>
                  {DIAS_SEMANA.map((dia) => (
                    <label
                      key={dia.value}
                      htmlFor={`batch-day-${dia.value}`}
                      style={{ display: 'flex', alignItems: 'center', gap: '7px', cursor: 'pointer', fontSize: '0.88rem' }}
                    >
                      <input
                        id={`batch-day-${dia.value}`}
                        type="checkbox"
                        checked={batchForm.daysOfWeek.includes(dia.value)}
                        onChange={() => cambiarDiaLote(dia.value)}
                      />
                      {dia.label}
                    </label>
                  ))}
                </div>
              </fieldset>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label" htmlFor="batch-start-date">Desde *</label>
                  <CampoFecha
                    id="batch-start-date"
                    required
                    value={batchForm.startDate}
                    onChange={(startDate) => setBatchForm({ ...batchForm, startDate })}
                  />
                </div>
                <div className="admin-form-group">
                  <label className="admin-label" htmlFor="batch-end-date">Hasta (incluido) *</label>
                  <CampoFecha
                    id="batch-end-date"
                    required
                    value={batchForm.endDate}
                    onChange={(endDate) => setBatchForm({ ...batchForm, endDate })}
                  />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="admin-form-group">
                  <label className="admin-label" htmlFor="batch-start-time">Hora del pase *</label>
                  <CampoHora
                    id="batch-start-time"
                    required
                    value={batchForm.startTime}
                    onChange={(startTime) => setBatchForm({ ...batchForm, startTime })}
                  />
                </div>
                <div className="admin-form-group">
                  <label className="admin-label" htmlFor="batch-ticket-price">Precio por entrada (€) *</label>
                  <input
                    id="batch-ticket-price"
                    type="number"
                    className="admin-input"
                    required
                    min={Number(salaLote?.basePrice || 0)}
                    step="0.05"
                    value={batchForm.ticketPrice}
                    onChange={(e) => setBatchForm({ ...batchForm, ticketPrice: e.target.value })}
                  />
                  <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                    Mínimo de la sala: {Number(salaLote?.basePrice || 0).toFixed(2)} €
                  </span>
                </div>
              </div>

              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 600, cursor: 'pointer', margin: '4px 0 10px' }}>
                <input
                  type="checkbox"
                  checked={batchForm.aLaVenta}
                  onChange={(e) => setBatchForm({ ...batchForm, aLaVenta: e.target.checked })}
                />
                Ponerlas a la venta al crearlas
              </label>

              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', margin: '4px 0 0' }}>
                El rango incluye ambas fechas. Las sesiones que se solapen no se crearán y aparecerán en el resumen.
                {batchForm.aLaVenta ? ' Las creadas se ponen «A la venta».' : ' Las creadas quedan como «Programadas».'}
              </p>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '22px', paddingTop: '16px', borderTop: '1px solid rgba(43,18,21,0.1)' }}>
                <button
                  type="button"
                  className="admin-btn admin-btn-secondary"
                  onClick={() => setBatchModal(false)}
                  disabled={batchGuardando}
                >
                  Cancelar
                </button>
                <button type="submit" className="admin-btn admin-btn-primary" disabled={batchGuardando}>
                  {batchGuardando ? (batchForm.aLaVenta ? 'Programando y poniendo a la venta…' : 'Programando…') : 'Programar lote'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
