import { useState, useMemo } from 'react';
import peliculas from '../../assets/peliculas.json';
import cines from '../../assets/cines.json';
import { getSeatLayout, getOcupadasSet } from '../../utils/butacas';

export default function TaquillaPage() {
  const [cineId, setCineId] = useState(cines[0].id);
  const [peliculaId, setPeliculaId] = useState(peliculas[0].id);
  const [sesionHora, setSesionHora] = useState('18:30');
  const [sesionSala, setSesionSala] = useState(1);
  const [cantidad, setCantidad] = useState(2);
  const [butacasElegidas, setButacasElegidas] = useState([]);
  const [medioPago, setMedioPago] = useState('tarjeta');
  const [efectivoEntregado, setEfectivoEntregado] = useState('');
  const [ticketEmitido, setTicketEmitido] = useState(null);
  const [errorValidacion, setErrorValidacion] = useState('');

  const cineSel = cines.find((c) => c.id === Number(cineId)) || cines[0];
  const peliSel = peliculas.find((p) => p.id === Number(peliculaId)) || peliculas[0];

  // Catálogo de sesiones disponibles para la taquilla
  const sesionesDisponibles = useMemo(() => [
    { hora: '16:00', sala: 1, formato: '2D',   precio: 9.00 },
    { hora: '18:30', sala: 2, formato: '2D',   precio: 9.50 },
    { hora: '19:45', sala: 5, formato: 'IMAX', precio: 14.00 },
    { hora: '20:15', sala: 3, formato: '3D',   precio: 11.50 },
    { hora: '21:30', sala: 4, formato: 'VIP',  precio: 15.00 },
    { hora: '22:45', sala: 5, formato: 'IMAX', precio: 14.00 },
  ], []);

  const sesionActual = sesionesDisponibles.find(
    (s) => s.hora === sesionHora && s.sala === Number(sesionSala)
  ) || sesionesDisponibles[0];

  // Matriz de asientos de la sala seleccionada
  const layout = useMemo(
    () => getSeatLayout(cineSel.id, sesionActual.sala),
    [cineSel.id, sesionActual.sala]
  );

  const { filas, cols, tipo } = layout;
  const totalAsientos = filas * cols;

  // Asientos ocupados en esta sesión
  const ocupadasSet = useMemo(
    () => getOcupadasSet(cineSel.id, peliSel.id, sesionActual.sala, sesionActual.hora),
    [cineSel.id, peliSel.id, sesionActual.sala, sesionActual.hora]
  );

  const precioUnitario = sesionActual.precio;
  const totalImporte = (precioUnitario * cantidad).toFixed(2);

  // Cambio si paga en efectivo
  const cambio = useMemo(() => {
    if (medioPago !== 'efectivo' || !efectivoEntregado) return null;
    const entregado = parseFloat(efectivoEntregado);
    if (isNaN(entregado) || entregado < parseFloat(totalImporte)) return null;
    return (entregado - parseFloat(totalImporte)).toFixed(2);
  }, [medioPago, efectivoEntregado, totalImporte]);

  // Manejar cambio de sesión o sala (resetea las butacas elegidas)
  function handleCambiarSesion(e) {
    const [h, s] = e.target.value.split('|');
    setSesionHora(h);
    setSesionSala(Number(s));
    setButacasElegidas([]);
    setErrorValidacion('');
  }

  function handleCambiarPelicula(e) {
    setPeliculaId(Number(e.target.value));
    setButacasElegidas([]);
    setErrorValidacion('');
  }

  function handleCambiarCine(e) {
    setCineId(Number(e.target.value));
    setButacasElegidas([]);
    setErrorValidacion('');
  }

  function handleCambiarCantidad(nuevaCantidad) {
    const c = Math.max(1, Math.min(10, nuevaCantidad));
    setCantidad(c);
    // Si hay más butacas seleccionadas que la nueva cantidad, recortamos
    if (butacasElegidas.length > c) {
      setButacasElegidas(butacasElegidas.slice(0, c));
    }
    setErrorValidacion('');
  }

  // Selección manual de butaca
  function toggleSeat(idx) {
    if (ocupadasSet.has(idx)) return;
    setErrorValidacion('');

    setButacasElegidas((prev) => {
      if (prev.includes(idx)) {
        return prev.filter((s) => s !== idx);
      }
      if (prev.length >= cantidad) {
        // Reemplazar la primera seleccionada si ya llegó al límite de entradas
        return [...prev.slice(1), idx];
      }
      return [...prev, idx];
    });
  }

  // Convertir índice numérico a etiqueta legible (Ej: Fila D, Asiento 7 -> "D7")
  function formatSeatLabel(idx) {
    const f = Math.floor(idx / cols);
    const c = (idx % cols) + 1;
    const letra = String.fromCharCode(65 + f);
    return `${letra}${c}`;
  }

  // Asignar automáticamente las mejores butacas contiguas
  function autoAsignarButacas() {
    setErrorValidacion('');
    const filaMedia = Math.floor(filas / 2);
    const ordenFilas = [];
    for (let offset = 0; offset < filas; offset++) {
      if (filaMedia + offset < filas) ordenFilas.push(filaMedia + offset);
      if (offset > 0 && filaMedia - offset >= 0) ordenFilas.push(filaMedia - offset);
    }

    let encontradas = [];
    for (const f of ordenFilas) {
      const colCentro = Math.floor(cols / 2);
      // Buscar bloque contiguo centrado
      for (let inicio = Math.max(0, colCentro - cantidad); inicio <= cols - cantidad; inicio++) {
        let libre = true;
        const candidatos = [];
        for (let k = 0; k < cantidad; k++) {
          const idx = f * cols + (inicio + k);
          if (ocupadasSet.has(idx)) {
            libre = false;
            break;
          }
          candidatos.push(idx);
        }
        if (libre) {
          encontradas = candidatos;
          break;
        }
      }
      if (encontradas.length === cantidad) break;
    }

    if (encontradas.length === cantidad) {
      setButacasElegidas(encontradas);
    } else {
      // Si no hay contiguas, coger las primeras libres disponibles
      const libres = [];
      for (let i = 0; i < totalAsientos && libres.length < cantidad; i++) {
        if (!ocupadasSet.has(i)) libres.push(i);
      }
      setButacasElegidas(libres);
    }
  }

  // Emitir ticket en TPV
  function handleVender(e) {
    e.preventDefault();

    if (butacasElegidas.length !== cantidad) {
      setErrorValidacion(
        `Debes seleccionar exactamente ${cantidad} butaca${cantidad > 1 ? 's' : ''} en el mapa de sala antes de emitir las entradas (actualmente seleccionadas: ${butacasElegidas.length}).`
      );
      return;
    }

    const ticketId = `TPV-${Math.floor(100000 + Math.random() * 900000)}`;
    const butacasNombres = butacasElegidas.map(formatSeatLabel).sort().join(', ');

    setTicketEmitido({
      id: ticketId,
      cine: cineSel.name,
      direccion: cineSel.address,
      ciudad: cineSel.city,
      pelicula: peliSel.title,
      hora: sesionActual.hora,
      sala: sesionActual.sala,
      salaTipo: tipo,
      formato: sesionActual.formato,
      entradas: cantidad,
      butacasTexto: butacasNombres,
      precioUnitario: precioUnitario.toFixed(2),
      total: totalImporte,
      medioPago: medioPago === 'tarjeta' ? 'Tarjeta / Datáfono' : 'Efectivo en Taquilla',
      horaEmision: new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' }),
      fechaEmision: new Date().toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' }),
    });

    setErrorValidacion('');
  }

  function handleNuevaVenta() {
    setTicketEmitido(null);
    setButacasElegidas([]);
    setEfectivoEntregado('');
    setErrorValidacion('');
  }

  const filasArr = Array.from({ length: filas }, (_, f) => f);
  const colsArr = Array.from({ length: cols }, (_, c) => c);

  return (
    <div className="admin-page-container">
      <header className="admin-header">
        <div>
          <h1 className="admin-header-title">Terminal TPV Taquilla Presencial</h1>
          <p className="admin-header-desc">
            Expedición de entradas con selección visual de butacas en sala y cobro en mostrador.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span className="admin-badge admin-badge-dark">{cineSel.name}</span>
        </div>
      </header>

      {/* Grid del TPV: Formulario + Mapa a la izquierda / Ticket impreso a la derecha */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: ticketEmitido ? '1fr 380px' : '1.3fr 0.7fr',
        gap: '24px',
        alignItems: 'start'
      }}>
        {/* COLUMNA 1: Parámetros de la venta y Mapa de Butacas */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Panel de Selección de Sesión */}
          <div className="admin-panel" style={{ margin: 0 }}>
            <h2 style={{ fontSize: '1.15rem', color: 'var(--burgundy)', margin: '0 0 16px', fontWeight: 700, fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" }}>
              Selección de Proyección y Pase
            </h2>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '14px' }}>
              <div className="admin-form-group">
                <label className="admin-label">Cine</label>
                <select
                  className="admin-select"
                  value={cineId}
                  onChange={handleCambiarCine}
                >
                  {cines.map((c) => (
                    <option key={c.id} value={c.id}>{c.name} ({c.city})</option>
                  ))}
                </select>
              </div>

              <div className="admin-form-group">
                <label className="admin-label">Película</label>
                <select
                  className="admin-select"
                  value={peliculaId}
                  onChange={handleCambiarPelicula}
                >
                  {peliculas.map((p) => (
                    <option key={p.id} value={p.id}>{p.title}</option>
                  ))}
                </select>
              </div>

              <div className="admin-form-group">
                <label className="admin-label">Pase / Sesión</label>
                <select
                  className="admin-select"
                  value={`${sesionHora}|${sesionSala}`}
                  onChange={handleCambiarSesion}
                >
                  {sesionesDisponibles.map((s) => (
                    <option key={`${s.hora}-${s.sala}`} value={`${s.hora}|${s.sala}`}>
                      {s.hora} — Sala {s.sala} ({s.formato}) — {s.precio.toFixed(2)} €
                    </option>
                  ))}
                </select>
              </div>

              <div className="admin-form-group">
                <label className="admin-label">Nº de Entradas</label>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <button
                    type="button"
                    className="admin-btn admin-btn-secondary admin-btn-sm"
                    style={{ width: '36px', height: '36px', fontSize: '1.2rem', padding: 0 }}
                    onClick={() => handleCambiarCantidad(cantidad - 1)}
                    disabled={cantidad <= 1}
                  >
                    −
                  </button>
                  <input
                    type="number"
                    min={1}
                    max={10}
                    className="admin-input"
                    value={cantidad}
                    onChange={(e) => handleCambiarCantidad(parseInt(e.target.value, 10) || 1)}
                    style={{ textAlign: 'center', fontWeight: 700, width: '60px' }}
                  />
                  <button
                    type="button"
                    className="admin-btn admin-btn-secondary admin-btn-sm"
                    style={{ width: '36px', height: '36px', fontSize: '1.2rem', padding: 0 }}
                    onClick={() => handleCambiarCantidad(cantidad + 1)}
                    disabled={cantidad >= 10}
                  >
                    +
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* Panel de Selección Visual de Butacas */}
          <div className="admin-panel" style={{ margin: 0 }}>
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '14px',
              flexWrap: 'wrap',
              gap: '10px'
            }}>
              <div>
                <h2 style={{ fontSize: '1.15rem', color: 'var(--burgundy)', margin: '0 0 2px', fontWeight: 700, fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" }}>
                  Elección de Butacas en Sala {sesionActual.sala} ({tipo})
                </h2>
                <span style={{ fontSize: '0.84rem', color: 'var(--text-muted)' }}>
                  {filas} filas × {cols} columnas (Aforo: {totalAsientos} asientos · Libres: {totalAsientos - ocupadasSet.size})
                </span>
              </div>

              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  type="button"
                  className="admin-btn admin-btn-secondary admin-btn-sm"
                  onClick={autoAsignarButacas}
                  title="Asigna automáticamente las butacas contiguas mejor ubicadas"
                >
                  Asignar mejores butacas
                </button>
                {butacasElegidas.length > 0 && (
                  <button
                    type="button"
                    className="admin-btn admin-btn-danger admin-btn-sm"
                    onClick={() => setButacasElegidas([])}
                  >
                    Limpiar selección
                  </button>
                )}
              </div>
            </div>

            {/* Barra de estado de selección */}
            <div style={{
              padding: '10px 14px',
              borderRadius: '4px',
              background: butacasElegidas.length === cantidad ? 'rgba(40,167,69,0.1)' : 'rgba(217,164,65,0.14)',
              border: `1px solid ${butacasElegidas.length === cantidad ? 'rgba(40,167,69,0.3)' : 'rgba(217,164,65,0.35)'}`,
              marginBottom: '16px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              fontSize: '0.88rem'
            }}>
              <div>
                Estado: <strong>{butacasElegidas.length} de {cantidad} butacas seleccionadas</strong>
                {butacasElegidas.length > 0 && (
                  <span style={{ marginLeft: '10px', color: 'var(--burgundy)', fontWeight: 700 }}>
                    [{butacasElegidas.map(formatSeatLabel).sort().join(', ')}]
                  </span>
                )}
              </div>
              {butacasElegidas.length < cantidad && (
                <span style={{ color: 'var(--granate)', fontSize: '0.8rem', fontWeight: 600 }}>
                  Faltan {cantidad - butacasElegidas.length} por asignar
                </span>
              )}
            </div>

            {errorValidacion && (
              <div style={{
                background: 'rgba(168,28,43,0.1)',
                border: '1px solid rgba(168,28,43,0.35)',
                color: '#a81c2b',
                padding: '10px 14px',
                borderRadius: '4px',
                marginBottom: '16px',
                fontSize: '0.86rem',
                fontWeight: 600
              }}>
                {errorValidacion}
              </div>
            )}

            {/* Pantalla de la sala */}
            <div className="compra-pantalla" style={{ marginBottom: '14px' }}>
              <div className="compra-pantalla-barra" style={{ width: '80%' }} />
              <span style={{ fontSize: '0.72rem', letterSpacing: '0.15em' }}>PANTALLA DE PROYECCIÓN</span>
            </div>

            {/* Leyenda */}
            <div className="compra-leyenda" style={{ marginBottom: '12px', fontSize: '0.78rem' }}>
              <span className="compra-leyenda-item"><span className="butaca butaca--libre" /> Libre</span>
              <span className="compra-leyenda-item"><span className="butaca butaca--ocupada" /> Ocupada / Vendida</span>
              <span className="compra-leyenda-item"><span className="butaca butaca--elegida" /> Seleccionada Staff</span>
            </div>

            {/* Matriz interactiva de butacas */}
            <div className="compra-mapa" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '10px 0' }}>
              <div className="compra-mapa-fila">
                <span className="compra-fila-letra" />
                {colsArr.map((c) => (
                  <span key={c} className="compra-col-num">{c + 1}</span>
                ))}
              </div>

              {filasArr.map((f) => (
                <div key={f} className="compra-mapa-fila">
                  <span className="compra-fila-letra">{String.fromCharCode(65 + f)}</span>
                  {colsArr.map((c) => {
                    const idx = f * cols + c;
                    const ocupada = ocupadasSet.has(idx);
                    const elegida = butacasElegidas.includes(idx);
                    return (
                      <button
                        key={idx}
                        type="button"
                        className={`butaca${ocupada ? ' butaca--ocupada' : elegida ? ' butaca--elegida' : ' butaca--libre'}`}
                        onClick={() => toggleSeat(idx)}
                        disabled={ocupada}
                        title={`Fila ${String.fromCharCode(65 + f)}, Butaca ${c + 1}${ocupada ? ' (Ocupada)' : ''}`}
                      />
                    );
                  })}
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* COLUMNA 2: Cobro y Ticket */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Panel de Cobro */}
          <div className="admin-panel" style={{ margin: 0 }}>
            <h2 style={{ fontSize: '1.15rem', color: 'var(--burgundy)', margin: '0 0 16px', fontWeight: 700, fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" }}>
              Cobro y Emisión
            </h2>

            <form onSubmit={handleVender}>
              <div style={{
                background: 'rgba(43,18,21,0.04)',
                border: '1px solid rgba(43,18,21,0.12)',
                borderRadius: '4px',
                padding: '12px 16px',
                marginBottom: '16px',
                fontSize: '0.88rem',
                lineHeight: 1.6
              }}>
                <div>Película: <strong>{peliSel.title}</strong></div>
                <div>Sesión: <strong>{sesionActual.hora} ({sesionActual.formato})</strong></div>
                <div>Sala: <strong>Sala {sesionActual.sala}</strong></div>
                <div>Entradas: <strong>{cantidad} × {precioUnitario.toFixed(2)} €</strong></div>
                <div>
                  Butacas: <strong style={{ color: 'var(--granate)' }}>
                    {butacasElegidas.length > 0 ? butacasElegidas.map(formatSeatLabel).sort().join(', ') : 'Sin asignar'}
                  </strong>
                </div>
              </div>

              <div className="admin-form-group">
                <label className="admin-label">Método de Pago</label>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                  <button
                    type="button"
                    className={`admin-btn ${medioPago === 'tarjeta' ? 'admin-btn-primary' : 'admin-btn-secondary'}`}
                    style={{ fontSize: '0.8rem', padding: '8px 10px' }}
                    onClick={() => setMedioPago('tarjeta')}
                  >
                    Tarjeta / TPV
                  </button>
                  <button
                    type="button"
                    className={`admin-btn ${medioPago === 'efectivo' ? 'admin-btn-primary' : 'admin-btn-secondary'}`}
                    style={{ fontSize: '0.8rem', padding: '8px 10px' }}
                    onClick={() => setMedioPago('efectivo')}
                  >
                    Efectivo
                  </button>
                </div>
              </div>

              {medioPago === 'efectivo' && (
                <div className="admin-form-group" style={{ marginTop: '10px' }}>
                  <label className="admin-label">Efectivo Entregado por Cliente (€)</label>
                  <input
                    type="number"
                    step="0.50"
                    placeholder="Ej: 20.00"
                    className="admin-input"
                    value={efectivoEntregado}
                    onChange={(e) => setEfectivoEntregado(e.target.value)}
                  />
                  {cambio !== null && (
                    <div style={{
                      marginTop: '6px',
                      padding: '6px 10px',
                      borderRadius: '4px',
                      background: 'rgba(40,167,69,0.1)',
                      color: '#155724',
                      fontWeight: 700,
                      fontSize: '0.85rem'
                    }}>
                      Cambio a devolver: {cambio} €
                    </div>
                  )}
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
                margin: '16px 0'
              }}>
                <span style={{ fontWeight: 700, color: 'var(--dark-burgundy)', textTransform: 'uppercase', fontSize: '0.85rem' }}>
                  Total a Cobrar:
                </span>
                <strong style={{ fontSize: '1.6rem', color: 'var(--granate)', fontVariantNumeric: 'tabular-nums' }}>
                  {totalImporte} €
                </strong>
              </div>

              <button
                type="submit"
                className="admin-btn admin-btn-primary"
                style={{ width: '100%', padding: '12px', fontSize: '0.95rem' }}
                disabled={butacasElegidas.length !== cantidad}
              >
                Cobrar e Imprimir Ticket
              </button>
            </form>
          </div>

          {/* Comprobante / Ticket Oficial emitido */}
          {ticketEmitido && (
            <div className="admin-panel" style={{
              margin: 0,
              background: '#ffffff',
              border: '1px solid #2c080c',
              boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
              fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
            }}>
              <div style={{ textAlign: 'center', marginBottom: '12px' }}>
                <strong style={{ fontSize: '1.15rem', color: 'var(--burgundy)', letterSpacing: '0.02em', display: 'block' }}>
                  CINASEE
                </strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Comprobante de Entrada · TPV Taquilla
                </span>
              </div>

              <div style={{
                borderTop: '1px dashed rgba(44, 8, 12, 0.35)',
                borderBottom: '1px dashed rgba(44, 8, 12, 0.35)',
                padding: '12px 0',
                margin: '10px 0',
                fontSize: '0.86rem',
                lineHeight: 1.6,
                fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
              }}>
                <div><strong>CINE:</strong> {ticketEmitido.cine}</div>
                <div><strong>DIRECCIÓN:</strong> {ticketEmitido.direccion}</div>
                <div><strong>PELÍCULA:</strong> {ticketEmitido.pelicula}</div>
                <div><strong>FECHA / HORA:</strong> {ticketEmitido.fechaEmision} · {ticketEmitido.hora} h</div>
                <div><strong>SALA:</strong> Sala {ticketEmitido.sala} ({ticketEmitido.formato})</div>
                <div style={{
                  background: 'rgba(142,31,43,0.08)',
                  padding: '6px 8px',
                  borderRadius: '3px',
                  margin: '6px 0',
                  color: 'var(--granate)',
                  fontWeight: 'bold',
                  fontSize: '0.92rem'
                }}>
                  BUTACAS ASIGNADAS: {ticketEmitido.butacasTexto}
                </div>
                <div><strong>Nº ENTRADAS:</strong> {ticketEmitido.entradas} ({ticketEmitido.precioUnitario} €/ud)</div>
                <div><strong>FORMA PAGO:</strong> {ticketEmitido.medioPago}</div>
                <div style={{ fontSize: '1.1rem', marginTop: '6px', fontWeight: 'bold', color: 'var(--burgundy)' }}>
                  TOTAL ABONADO: {ticketEmitido.total} €
                </div>
              </div>

              {/* Código de barras / Localizador */}
              <div style={{ textAlign: 'center', margin: '14px 0 10px' }}>
                <div style={{
                  display: 'inline-block',
                  background: '#2c080c',
                  color: '#fff',
                  padding: '6px 16px',
                  letterSpacing: '0.25em',
                  fontFamily: "system-ui, -apple-system, sans-serif",
                  fontSize: '0.9rem',
                  borderRadius: '2px'
                }}>
                  ||| | ||||| || ||| ||||
                </div>
                <p style={{ margin: '5px 0 0', fontSize: '0.8rem', color: '#555', fontFamily: "system-ui, -apple-system, sans-serif" }}>
                  Localizador: <strong>{ticketEmitido.id}</strong>
                </p>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginTop: '14px' }}>
                <button
                  type="button"
                  className="admin-btn admin-btn-secondary admin-btn-sm"
                  onClick={() => window.print()}
                >
                  Imprimir Ticket
                </button>
                <button
                  type="button"
                  className="admin-btn admin-btn-primary admin-btn-sm"
                  onClick={handleNuevaVenta}
                >
                  Nueva Venta
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
