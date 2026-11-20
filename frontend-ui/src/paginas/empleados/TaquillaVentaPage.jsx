import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/useAuth';
import { useCine } from '../../context/useCine';
import { cargarCarteleraCine, obtenerSala, fechaIso } from '../../api/cartelera';
import {
  obtenerButacasOcupadas,
  crearReserva,
  cancelarReserva,
  iniciarPagoRedsys,
  irARedsys,
  guardarPagoEnCurso,
  nombreButaca,
  DESCUENTOS,
} from '../../api/reservas';
import { obtenerPerfil } from '../../api/usuarios';
import { layoutDeSala, butacaDeIndice, indicesOcupados, etiquetaButaca } from '../../utils/butacas';
import MapaButacas from '../../components/MapaButacas';
import { AvisoAdmin } from '../admin/AdminAvisos';
import CampoFecha from '../../components/CampoFecha';

/* =========================================================
   Taquilla presencial
   ---------------------------------------------------------
   1. Sesiones A_LA_VENTA del cine (Cartelera, Comp. 1).
   2. Butacas libres de la sesión (Reservas, Comp. 2).
   3. POST /reservations con el token del taquillero (venta anónima,
      a un correo o a un cliente registrado por su id).
   4. Cobro con tarjeta en Redsys; la página de vuelta emite las entradas.
   ========================================================= */

const MAX_ENTRADAS = 10;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function TaquillaPage() {
  const { user, tokenUsuario } = useAuth();
  const token = tokenUsuario();
  const { cines } = useCine();

  // Cine: el asignado al taquillero (Comp. 3, cinemaId); el administrador puede elegir
  const [cineElegido, setCineElegido] = useState(null);
  const cineAsignado = cines.some((c) => c.id === user?.cinemaId) ? user.cinemaId : null;
  const cineId = cineElegido ?? cineAsignado ?? cines[0]?.id ?? null;
  const cine = cines.find((c) => c.id === cineId) || null;

  const [fecha, setFecha] = useState(() => fechaIso(new Date()));
  const [cartelera, setCartelera] = useState({ clave: null, peliculas: [], sesionesPorPelicula: {}, error: '' });
  const claveCartelera = `${cineId}|${fecha}`;
  const cargandoCartelera = Boolean(cineId && token) && cartelera.clave !== claveCartelera;

  const [peliculaId, setPeliculaId] = useState('');
  const [showId, setShowId] = useState('');
  const [sala, setSala] = useState({ roomId: null, layout: null });
  const [ocupadas, setOcupadas] = useState(null);

  const [cantidad, setCantidad] = useState(2);
  const [butacas, setButacas] = useState([]);
  const [cliente, setCliente] = useState({ tipo: 'anonimo', email: '', userId: '' });
  const [reserva, setReserva] = useState(null);
  const [procesando, setProcesando] = useState(false);
  const [aviso, setAviso] = useState(null);
  // Errores de reservar/cobrar: se muestran junto al botón, no arriba de la página
  const [errorCobro, setErrorCobro] = useState('');
  // Comprobación del nº de cliente contra Usuarios (Comp. 3)
  const [resultadoCliente, setResultadoCliente] = useState({ id: '', estado: '', nombre: '', texto: '' });
  const valorCliente = cliente.userId.trim();
  const idCliente = Number(valorCliente);
  const idClienteValido = cliente.tipo === 'registrado' && valorCliente !== '' && Number.isInteger(idCliente) && idCliente > 0;

  /* Comprueba el nº de cliente mientras se escribe (con una pequeña espera) */
  useEffect(() => {
    if (!idClienteValido || !token) return undefined;
    let cancelado = false;
    const espera = setTimeout(() => {
      obtenerPerfil(idCliente, token)
        .then((perfil) => {
          if (!cancelado) setResultadoCliente({ id: valorCliente, estado: 'ok', nombre: perfil.fullName || perfil.email || '', texto: '' });
        })
        .catch((err) => {
          if (cancelado) return;
          if (err.status === 404) {
            setResultadoCliente({ id: valorCliente, estado: 'noExiste', nombre: '', texto: '' });
          } else {
            // No se pudo comprobar (servicio caído, etc.): no se bloquea la venta, Reservas lo vuelve a validar
            setResultadoCliente({ id: valorCliente, estado: 'error', nombre: '', texto: err.message });
          }
        });
    }, 400);
    return () => { cancelado = true; clearTimeout(espera); };
  }, [idClienteValido, idCliente, valorCliente, token]);

  // Estado mostrado: 'vacio' | 'comprobando' | 'ok' | 'noExiste' | 'error'
  const clienteVerificado = !idClienteValido
    ? { id: valorCliente, estado: 'vacio', nombre: '', texto: '' }
    : resultadoCliente.id === valorCliente
      ? resultadoCliente
      : { id: valorCliente, estado: 'comprobando', nombre: '', texto: '' };

  const clienteNoValido = cliente.tipo === 'registrado'
    && (clienteVerificado.estado === 'noExiste' || clienteVerificado.estado === 'comprobando');

  /* Sesiones a la venta del cine en la fecha elegida */
  useEffect(() => {
    if (!cineId || !token) return undefined;
    let cancelado = false;
    const clave = `${cineId}|${fecha}`;
    cargarCarteleraCine(cineId, new Date(`${fecha}T00:00:00`), token)
      .then((res) => { if (!cancelado) setCartelera({ clave, ...res, error: '' }); })
      .catch((err) => { if (!cancelado) setCartelera({ clave, peliculas: [], sesionesPorPelicula: {}, error: err.message }); });
    return () => { cancelado = true; };
  }, [cineId, fecha, token]);

  const peliculas = cargandoCartelera ? [] : Array.from(new Map((cartelera.peliculas || []).map((p) => [p.id, p])).values());
  const pelicula = peliculas.find((p) => p.id === peliculaId) || peliculas[0] || null;
  const sesiones = pelicula ? cartelera.sesionesPorPelicula[pelicula.id] || [] : [];
  const sesion = sesiones.find((s) => s.showId === showId) || sesiones[0] || null;
  const layout = sala.roomId === sesion?.roomId ? sala.layout : null;

  /* Sala de la sesión (filas y columnas) */
  useEffect(() => {
    if (!sesion || !token) return undefined;
    let cancelado = false;
    obtenerSala(sesion.roomId, token)
      .then((room) => { if (!cancelado) setSala({ roomId: room.id, layout: layoutDeSala(room) }); })
      .catch((err) => { if (!cancelado) setAviso({ tipo: 'error', texto: `No se pudo cargar la sala: ${err.message}` }); });
    return () => { cancelado = true; };
  }, [sesion, token]);

  /* Butacas ocupadas de la sesión */
  const cargarOcupadas = useCallback(() => {
    if (!sesion || !layout) return undefined;
    let cancelado = false;
    obtenerButacasOcupadas(sesion.showId)
      .then((mapa) => { if (!cancelado) setOcupadas({ showId: sesion.showId, set: indicesOcupados(mapa.occupiedSeats, layout.cols) }); })
      .catch((err) => { if (!cancelado) setAviso({ tipo: 'error', texto: `No se pudo consultar el mapa de butacas: ${err.message}` }); });
    return () => { cancelado = true; };
  }, [sesion, layout]);

  useEffect(() => cargarOcupadas(), [cargarOcupadas]);

  const ocupadasSet = (ocupadas && sesion && ocupadas.showId === sesion.showId) ? ocupadas.set : null;

  function reiniciarSeleccion() {
    setButacas([]);
    setAviso(null);
    setErrorCobro('');
  }

  function cambiarCantidad(n) {
    const c = Math.max(1, Math.min(MAX_ENTRADAS, n));
    setCantidad(c);
    setButacas((prev) => prev.slice(0, c));
  }

  function toggleSeat(idx) {
    if (!ocupadasSet || ocupadasSet.has(idx)) return;
    setButacas((prev) => {
      if (prev.includes(idx)) return prev.filter((s) => s !== idx);
      if (prev.length >= cantidad) return [...prev.slice(1), idx];
      return [...prev, idx];
    });
  }

  /** Mejores butacas contiguas: filas centrales primero */
  function autoAsignar() {
    if (!layout || !ocupadasSet) return;
    const { filas, cols } = layout;
    const media = Math.floor(filas / 2);
    const orden = [];
    for (let d = 0; d < filas; d++) {
      if (media + d < filas) orden.push(media + d);
      if (d > 0 && media - d >= 0) orden.push(media - d);
    }
    for (const f of orden) {
      for (let inicio = Math.max(0, Math.floor(cols / 2) - cantidad); inicio <= cols - cantidad; inicio++) {
        const bloque = Array.from({ length: cantidad }, (_, k) => f * cols + inicio + k);
        if (bloque.every((i) => !ocupadasSet.has(i))) {
          setButacas(bloque);
          return;
        }
      }
    }
    setAviso({ tipo: 'info', texto: `No hay ${cantidad} butacas contiguas libres: elígelas en el mapa.` });
  }

  /** Datos del comprador que acepta Reservas para una venta de taquilla */
  function datosComprador() {
    if (cliente.tipo === 'email') {
      if (!EMAIL_REGEX.test(cliente.email.trim())) throw new Error('Introduce un correo electrónico válido.');
      return { guestEmail: cliente.email.trim() };
    }
    if (cliente.tipo === 'registrado') {
      const id = Number(cliente.userId);
      if (!Number.isInteger(id) || id <= 0) throw new Error('Introduce el número de cliente (id de usuario).');
      return { userId: id };
    }
    return {};
  }

  async function handleReservar(e) {
    e.preventDefault();
    setAviso(null);
    setErrorCobro('');
    if (butacas.length !== cantidad) {
      setErrorCobro(`Selecciona ${cantidad} butaca${cantidad > 1 ? 's' : ''} en el mapa.`);
      return;
    }
    setProcesando(true);
    try {
      const nueva = await crearReserva({
        showId: sesion.showId,
        seats: butacas.map((idx) => butacaDeIndice(idx, layout.cols)),
        ...datosComprador(),
      }, token);
      setReserva(nueva);
    } catch (err) {
      setErrorCobro(err.status === 404 && cliente.tipo === 'registrado'
        ? `No existe ningún cliente registrado con el número ${cliente.userId}.`
        : `No se pudo reservar: ${err.message}`);
      if (err.status === 409) {
        setButacas([]);
        cargarOcupadas();
      }
    } finally {
      setProcesando(false);
    }
  }

  async function handleCobrar() {
    setProcesando(true);
    setErrorCobro('');
    try {
      const formulario = await iniciarPagoRedsys(reserva.id, token);
      guardarPagoEnCurso({
        reservationId: reserva.id,
        token,
        taquilla: true,
        pelicula: pelicula.title,
        cine: cine?.name,
        sala: layout.tipo,
        fecha: sesion.startTime,
        hora: sesion.hora,
        butacas: (reserva.seats || []).map((s) => nombreButaca(s.row, s.column)),
        total: Number(reserva.totalAmount).toFixed(2),
      });
      irARedsys(formulario);
    } catch (err) {
      setProcesando(false);
      setErrorCobro(`No se pudo iniciar el cobro: ${err.message}`);
    }
  }

  async function handleAnular() {
    try {
      await cancelarReserva(reserva.id, token);
    } catch {
      // si ya caducó, las butacas ya están libres
    }
    setReserva(null);
    setButacas([]);
    cargarOcupadas();
  }

  const total = sesion ? (sesion.precio * cantidad).toFixed(2) : '0.00';
  const etiquetas = layout ? butacas.map((i) => etiquetaButaca(i, layout.cols)).sort().join(', ') : '';

  return (
    <div className="admin-page-container">
      <header className="admin-header">
        <div>
          <h1 className="admin-header-title">Terminal de Taquilla</h1>
          <p className="admin-header-desc">Venta presencial de entradas con selección de butacas y cobro con tarjeta.</p>
        </div>
        {cine && <span className="admin-badge admin-badge-dark">{cine.name}</span>}
      </header>

      {!token && <AvisoAdmin aviso={{ tipo: 'error', texto: 'Tu sesión ha caducado. Vuelve a iniciar sesión para vender entradas.' }} />}
      <AvisoAdmin aviso={aviso} onCerrar={() => setAviso(null)} />

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '24px', alignItems: 'flex-start' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px', flex: '2 1 520px', minWidth: 0 }}>
          {/* Selección de sesión */}
          <div className="admin-panel" style={{ margin: 0 }}>
            <h2 style={{ fontSize: '1.15rem', color: 'var(--burgundy)', margin: '0 0 16px', fontWeight: 700 }}>
              Proyección y pase
            </h2>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '14px' }}>
              <div className="admin-form-group">
                <label className="admin-label" htmlFor="tq-cine">Cine</label>
                <select
                  id="tq-cine"
                  className="admin-select"
                  value={cineId ?? ''}
                  onChange={(e) => { setCineElegido(e.target.value); setPeliculaId(''); setShowId(''); reiniciarSeleccion(); }}
                  disabled={(user?.rol !== 'ADMIN' && Boolean(cineAsignado)) || reserva !== null}
                >
                  {cines.length === 0 ? (
                    <option value="">Sin cines disponibles</option>
                  ) : (
                    cines.map((c) => <option key={c.id} value={c.id}>{c.name} ({c.city})</option>)
                  )}
                </select>
              </div>

              <div className="admin-form-group">
                <label className="admin-label" htmlFor="tq-fecha">Fecha</label>
                <CampoFecha
                  id="tq-fecha"
                  required
                  value={fecha}
                  min={fechaIso(new Date())}
                  onChange={(nueva) => { setFecha(nueva); setPeliculaId(''); setShowId(''); reiniciarSeleccion(); }}
                  disabled={reserva !== null}
                />
              </div>

              <div className="admin-form-group">
                <label className="admin-label" htmlFor="tq-peli">Película</label>
                <select
                  id="tq-peli"
                  className="admin-select"
                  value={pelicula?.id ?? ''}
                  onChange={(e) => { setPeliculaId(e.target.value); setShowId(''); reiniciarSeleccion(); }}
                  disabled={peliculas.length === 0 || reserva !== null}
                >
                  {peliculas.length === 0 && <option value="">{cargandoCartelera ? 'Cargando…' : 'Sin sesiones a la venta'}</option>}
                  {peliculas.map((p) => <option key={p.id} value={p.id}>{p.title}</option>)}
                </select>
              </div>

              <div className="admin-form-group">
                <label className="admin-label" htmlFor="tq-sesion">Pase</label>
                <select
                  id="tq-sesion"
                  className="admin-select"
                  value={sesion?.showId ?? ''}
                  onChange={(e) => { setShowId(e.target.value); reiniciarSeleccion(); }}
                  disabled={sesiones.length === 0 || reserva !== null}
                >
                  {sesiones.map((s) => (
                    <option key={s.showId} value={s.showId}>
                      {s.hora}, {s.salaNombre} ({s.formato}), {Number(s.precio || 0).toFixed(2)} €
                    </option>
                  ))}
                </select>
              </div>

              <div className="admin-form-group">
                <label className="admin-label" htmlFor="tq-cantidad">Nº de entradas</label>
                <input
                  id="tq-cantidad"
                  type="number"
                  min={1}
                  max={MAX_ENTRADAS}
                  className="admin-input"
                  value={cantidad}
                  onChange={(e) => cambiarCantidad(parseInt(e.target.value, 10) || 1)}
                  disabled={reserva !== null}
                />
              </div>
            </div>

            {cartelera.error && !cargandoCartelera && (
              <div className="cinasee-error" role="alert">No se pudieron cargar las sesiones: {cartelera.error}</div>
            )}
          </div>

          {/* Mapa de butacas */}
          {sesion && layout && (
            <div className="admin-panel" style={{ margin: 0 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', flexWrap: 'wrap', gap: '10px' }}>
                <div>
                  <h2 style={{ fontSize: '1.15rem', color: 'var(--burgundy)', margin: '0 0 2px', fontWeight: 700 }}>
                    Butacas en {layout.tipo}
                  </h2>
                  <span style={{ fontSize: '0.84rem', color: 'var(--text-muted)' }}>
                    {layout.filas} filas × {layout.cols} columnas
                    {ocupadasSet && `, libres: ${layout.filas * layout.cols - ocupadasSet.size}`}
                  </span>
                </div>
                {reserva === null && (
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button type="button" className="admin-btn admin-btn-secondary admin-btn-sm" onClick={autoAsignar} disabled={!ocupadasSet}>
                      Asignar mejores butacas
                    </button>
                    {butacas.length > 0 && (
                      <button type="button" className="admin-btn admin-btn-danger admin-btn-sm" onClick={() => setButacas([])}>
                        Limpiar selección
                      </button>
                    )}
                  </div>
                )}
              </div>

              {ocupadasSet ? (
                <MapaButacas
                  layout={layout}
                  ocupadas={ocupadasSet}
                  seleccionadas={butacas}
                  onToggle={reserva === null ? toggleSeat : () => {}}
                />
              ) : (
                <p style={{ color: 'var(--text-muted)' }}>Consultando butacas libres…</p>
              )}
            </div>
          )}
        </div>

        {/* Cobro */}
        <div className="admin-panel" style={{ margin: 0, flex: '1 1 300px', minWidth: 0 }}>
          <h2 style={{ fontSize: '1.15rem', color: 'var(--burgundy)', margin: '0 0 16px', fontWeight: 700 }}>
            Cobro y emisión
          </h2>

          {!sesion ? (
            <p style={{ color: 'var(--text-muted)' }}>
              {cargandoCartelera ? 'Cargando sesiones…' : 'No hay sesiones a la venta para este cine y fecha.'}
            </p>
          ) : reserva === null ? (
            <form onSubmit={handleReservar}>
              <div style={{
                background: 'rgba(43,18,21,0.04)',
                border: '1px solid rgba(43,18,21,0.12)',
                borderRadius: '4px',
                padding: '12px 16px',
                marginBottom: '16px',
                fontSize: '0.88rem',
                lineHeight: 1.6,
              }}>
                <div>Película: <strong>{pelicula?.title || 'Sin seleccionar'}</strong></div>
                <div>Sesión: <strong>{[sesion?.hora, sesion?.salaNombre].filter(Boolean).join(', ')}</strong></div>
                <div>Entradas: <strong>{cantidad} × {Number(sesion?.precio || 0).toFixed(2)} €</strong></div>
                <div>Butacas: <strong style={{ color: 'var(--granate)' }}>{etiquetas || 'Sin asignar'}</strong></div>
              </div>

              <div className="admin-form-group">
                <label className="admin-label" htmlFor="tq-cliente">Comprador</label>
                <select
                  id="tq-cliente"
                  className="admin-select"
                  value={cliente.tipo}
                  onChange={(e) => setCliente({ ...cliente, tipo: e.target.value })}
                >
                  <option value="anonimo">Venta anónima</option>
                  <option value="email">Enviar a un correo</option>
                  <option value="registrado">Cliente registrado (suma puntos)</option>
                </select>
              </div>
              {cliente.tipo === 'email' && (
                <div className="admin-form-group">
                  <label className="admin-label" htmlFor="tq-email">Correo del comprador</label>
                  <input
                    id="tq-email"
                    type="email"
                    className="admin-input"
                    value={cliente.email}
                    onChange={(e) => setCliente({ ...cliente, email: e.target.value })}
                  />
                </div>
              )}
              {cliente.tipo === 'registrado' && (
                <div className="admin-form-group">
                  <label className="admin-label" htmlFor="tq-userid">Número de cliente</label>
                  <input
                    id="tq-userid"
                    type="number"
                    min={1}
                    className="admin-input"
                    placeholder="Ej. 1"
                    value={cliente.userId}
                    onChange={(e) => { setCliente({ ...cliente, userId: e.target.value }); setErrorCobro(''); }}
                    aria-invalid={clienteVerificado.estado === 'noExiste'}
                    aria-describedby="tq-userid-estado"
                    style={clienteVerificado.estado === 'noExiste' ? { borderColor: '#d9383a', background: '#fff5f5' } : undefined}
                  />
                  <small id="tq-userid-estado" role="status" style={{ fontSize: '0.8rem', marginTop: '4px', display: 'block', color: 'var(--text-muted)' }}>
                    {clienteVerificado.estado === 'comprobando' && 'Comprobando cliente…'}
                    {clienteVerificado.estado === 'ok' && (
                      <span style={{ color: '#1e7b34', fontWeight: 600 }}>{clienteVerificado.nombre || `Cliente nº ${clienteVerificado.id}`}</span>
                    )}
                    {clienteVerificado.estado === 'noExiste' && (
                      <span style={{ color: '#d9383a', fontWeight: 600 }}>No existe ningún cliente registrado con ese número.</span>
                    )}
                    {clienteVerificado.estado === 'error' && `No se pudo comprobar el cliente (${clienteVerificado.texto}).`}
                    {clienteVerificado.estado === 'vacio' && 'Indica el nº de cliente que el usuario tiene en su perfil para sumarle puntos.'}
                  </small>
                </div>
              )}

              <div style={{
                background: 'rgba(217,164,65,0.18)',
                border: '1px solid rgba(217,164,65,0.45)',
                padding: '12px 16px',
                borderRadius: '4px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                margin: '16px 0',
              }}>
                <span style={{ fontWeight: 700, color: 'var(--dark-burgundy)', textTransform: 'uppercase', fontSize: '0.85rem' }}>
                  Importe estimado:
                </span>
                <strong style={{ fontSize: '1.4rem', color: 'var(--granate)' }}>{total} €</strong>
              </div>

              {errorCobro && <div className="cinasee-error" role="alert" style={{ marginBottom: '12px' }}>{errorCobro}</div>}

              <button
                type="submit"
                className="admin-btn admin-btn-primary"
                style={{ width: '100%', padding: '12px', fontSize: '0.95rem' }}
                disabled={butacas.length !== cantidad || procesando || !token || clienteNoValido}
              >
                {procesando ? 'Bloqueando butacas…' : 'Reservar butacas'}
              </button>
            </form>
          ) : (
            <div>
              <div className="cinasee-aviso cinasee-aviso--ok">
                Butacas {(reserva.seats || []).map((s) => nombreButaca(s.row, s.column)).join(', ')} bloqueadas
                durante 10 minutos (reserva nº {reserva.id}).
              </div>
              <p style={{ fontSize: '0.9rem' }}>
                Precio por entrada: {Number(reserva.unitPrice).toFixed(2)} €
                {DESCUENTOS[reserva.discountApplied] && `, ${DESCUENTOS[reserva.discountApplied]}`}
              </p>
              <p style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--granate)' }}>
                Total a cobrar: {Number(reserva.totalAmount).toFixed(2)} €
              </p>
              {errorCobro && <div className="cinasee-error" role="alert" style={{ marginBottom: '12px' }}>{errorCobro}</div>}
              <button
                type="button"
                className="admin-btn admin-btn-primary"
                style={{ width: '100%', padding: '12px', fontSize: '0.95rem', marginBottom: '8px' }}
                onClick={handleCobrar}
                disabled={procesando}
              >
                {procesando ? 'Conectando con Redsys…' : 'Cobrar con tarjeta (Redsys)'}
              </button>
              <button
                type="button"
                className="admin-btn admin-btn-secondary"
                style={{ width: '100%' }}
                onClick={handleAnular}
                disabled={procesando}
              >
                Anular y liberar butacas
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
