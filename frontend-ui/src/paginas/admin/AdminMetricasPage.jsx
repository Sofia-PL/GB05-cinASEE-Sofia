import { useState, useEffect } from 'react';
import { useAuth } from '../../context/useAuth';
import { useCine } from '../../context/useCine';
import { obtenerPeliculas, obtenerSalasCine, fechaIso, TIPOS_SALA, nombreGenero } from '../../api/cartelera';
import { obtenerTaquilla, obtenerOcupacion } from '../../api/analitica';
import { AvisoAdmin, AvisoSinToken } from './AdminAvisos';

/* =========================================================
   Métricas (Componente 4 · Analítica)
   - Taquilla: GET /reports/box-office (total y por película).
   - Ocupación: GET /metrics/occupancy por sala del cine elegido.
   ========================================================= */

const DIAS = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

function rangoDe(periodo, desde, hasta) {
  const hoy = new Date();
  if (periodo === 'hoy') return { startDate: fechaIso(hoy), endDate: fechaIso(hoy), texto: 'Hoy' };
  if (periodo === 'semana') {
    const inicio = new Date(hoy);
    inicio.setDate(hoy.getDate() - 6);
    return { startDate: fechaIso(inicio), endDate: fechaIso(hoy), texto: 'Últimos 7 días' };
  }
  if (periodo === 'mes') {
    return { startDate: fechaIso(new Date(hoy.getFullYear(), hoy.getMonth(), 1)), endDate: fechaIso(hoy), texto: 'Mes en curso' };
  }
  return { startDate: desde, endDate: hasta, texto: `Del ${desde} al ${hasta}` };
}

const euros = (v) => `${Number(v || 0).toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €`;

function Kpi({ titulo, valor }) {
  return (
    <div className="admin-panel" style={{ margin: 0 }}>
      <span className="admin-label" style={{ color: 'var(--text-muted)' }}>{titulo}</span>
      <div style={{ fontSize: '2.2rem', fontWeight: 800, color: 'var(--burgundy)', margin: '8px 0 4px', fontVariantNumeric: 'tabular-nums' }}>
        {valor}
      </div>
    </div>
  );
}

export default function AdminMetricasPage() {
  const { tokenUsuario } = useAuth();
  const token = tokenUsuario();
  const { cines } = useCine();

  const [periodo, setPeriodo] = useState('semana');
  const [desde, setDesde] = useState(() => fechaIso(new Date()));
  const [hasta, setHasta] = useState(() => fechaIso(new Date()));
  const rango = rangoDe(periodo, desde, hasta);
  const rangoValido = rango.startDate && rango.endDate && rango.startDate <= rango.endDate;

  const [taquilla, setTaquilla] = useState({ clave: null, total: null, porPelicula: [], error: '' });
  const claveTaquilla = `${rango.startDate}|${rango.endDate}`;

  const [cineElegido, setCineElegido] = useState(null);
  const cineId = cineElegido ?? cines[0]?.id ?? null;
  const [diaSemana, setDiaSemana] = useState('');
  const [ocupacion, setOcupacion] = useState({ clave: null, cine: null, salas: [], error: '' });
  const claveOcupacion = `${cineId}|${diaSemana}`;

  const [aviso, setAviso] = useState(null);

  /* Taquilla: total del periodo y desglose por película */
  useEffect(() => {
    if (!token || !rangoValido) return undefined;
    let cancelado = false;
    const clave = `${rango.startDate}|${rango.endDate}`;
    const fechas = { startDate: rango.startDate, endDate: rango.endDate };
    Promise.all([obtenerTaquilla(fechas, token), obtenerPeliculas()])
      .then(async ([total, pelis]) => {
        const filas = await Promise.all((pelis || []).map(async (p) => {
          try {
            const r = await obtenerTaquilla({ ...fechas, movieId: p.id }, token);
            return { pelicula: p, entradas: r.totalTicketsSold || 0, ingresos: r.grossRevenue || 0 };
          } catch {
            return { pelicula: p, entradas: 0, ingresos: 0 };
          }
        }));
        if (cancelado) return;
        setTaquilla({
          clave,
          total,
          porPelicula: filas.filter((f) => f.entradas > 0).sort((a, b) => b.ingresos - a.ingresos),
          error: '',
        });
      })
      .catch((err) => { if (!cancelado) setTaquilla({ clave, total: null, porPelicula: [], error: err.message }); });
    return () => { cancelado = true; };
  }, [token, rango.startDate, rango.endDate, rangoValido]);

  /* Ocupación del cine elegido y de cada una de sus salas */
  useEffect(() => {
    if (!token || !cineId) return undefined;
    let cancelado = false;
    const clave = `${cineId}|${diaSemana}`;
    const filtroDia = diaSemana ? { dayOfWeek: Number(diaSemana) } : {};
    Promise.all([
      obtenerOcupacion({ cinemaId: cineId, ...filtroDia }, token),
      obtenerSalasCine(cineId, token),
    ])
      .then(async ([delCine, salas]) => {
        const porSala = await Promise.all((salas || []).map(async (s) => {
          try {
            return { sala: s, metrica: await obtenerOcupacion({ cinemaId: cineId, screenId: s.id, ...filtroDia }, token) };
          } catch {
            return { sala: s, metrica: null };
          }
        }));
        if (!cancelado) setOcupacion({ clave, cine: delCine, salas: porSala, error: '' });
      })
      .catch((err) => { if (!cancelado) setOcupacion({ clave, cine: null, salas: [], error: err.message }); });
    return () => { cancelado = true; };
  }, [token, cineId, diaSemana]);

  const cargandoTaquilla = Boolean(token && rangoValido) && taquilla.clave !== claveTaquilla;
  const cargandoOcupacion = Boolean(token && cineId) && ocupacion.clave !== claveOcupacion;
  const total = taquilla.clave === claveTaquilla ? taquilla.total : null;
  const entradasTotales = total?.totalTicketsSold || 0;
  const ingresosTotales = total?.grossRevenue || 0;

  return (
    <div className="admin-page-container">
      <header className="admin-header">
        <div>
          <h1 className="admin-header-title">Métricas de Rendimiento</h1>
          <p className="admin-header-desc">Taquilla, entradas vendidas y ocupación de salas (Componente de Analítica).</p>
        </div>
      </header>

      {!token && <AvisoSinToken />}
      <AvisoAdmin aviso={aviso} onCerrar={() => setAviso(null)} />

      {/* Periodo */}
      <div className="admin-tab-bar" style={{ justifyContent: 'space-between', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
          {[['hoy', 'Hoy'], ['semana', 'Últimos 7 días'], ['mes', 'Este mes'], ['rango', 'Rango de fechas']].map(([valor, texto]) => (
            <button
              key={valor}
              type="button"
              className={`admin-tab-btn${periodo === valor ? ' active' : ''}`}
              onClick={() => setPeriodo(valor)}
            >
              {texto}
            </button>
          ))}
        </div>

        {periodo === 'rango' && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', paddingBottom: '6px', flexWrap: 'wrap' }}>
            <label className="admin-label" htmlFor="met-desde" style={{ margin: 0 }}>Desde</label>
            <input id="met-desde" type="date" className="admin-input" value={desde} onChange={(e) => setDesde(e.target.value)} style={{ width: '160px' }} />
            <label className="admin-label" htmlFor="met-hasta" style={{ margin: 0 }}>Hasta</label>
            <input id="met-hasta" type="date" className="admin-input" value={hasta} onChange={(e) => setHasta(e.target.value)} style={{ width: '160px' }} />
          </div>
        )}
      </div>

      <p style={{ margin: '-10px 0 20px', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
        {rangoValido ? rango.texto : 'La fecha de inicio no puede ser posterior a la de fin.'}
        {cargandoTaquilla && ' · Cargando…'}
      </p>

      {taquilla.error && taquilla.clave === claveTaquilla && (
        <AvisoAdmin aviso={{ tipo: 'error', texto: `No se pudo cargar la taquilla: ${taquilla.error}` }} />
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '18px', marginBottom: '28px' }}>
        <Kpi titulo="Ingresos de taquilla" valor={total ? euros(ingresosTotales) : '—'} />
        <Kpi titulo="Entradas vendidas" valor={total ? entradasTotales.toLocaleString('es-ES') : '—'} />
        <Kpi titulo="Precio medio por entrada" valor={total && entradasTotales > 0 ? euros(ingresosTotales / entradasTotales) : '—'} />
      </div>

      {/* Recaudación por película */}
      <div className="admin-panel" style={{ marginBottom: '28px' }}>
        <h3 style={{ margin: '0 0 16px', color: 'var(--burgundy)', fontSize: '1.2rem', fontWeight: 700 }}>
          Recaudación por película
        </h3>
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Película</th>
                <th>Género</th>
                <th style={{ textAlign: 'right' }}>Entradas</th>
                <th style={{ textAlign: 'right' }}>Ingresos</th>
                <th style={{ textAlign: 'right' }}>Cuota</th>
              </tr>
            </thead>
            <tbody>
              {!cargandoTaquilla && taquilla.porPelicula.length === 0 ? (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', padding: '24px', color: 'var(--text-muted)' }}>
                    No hay ventas en este periodo.
                  </td>
                </tr>
              ) : (
                taquilla.porPelicula.map((f) => (
                  <tr key={f.pelicula.id}>
                    <td><strong style={{ color: 'var(--burgundy)' }}>{f.pelicula.title}</strong></td>
                    <td><span className="admin-badge admin-badge-dark">{nombreGenero(f.pelicula.genre)}</span></td>
                    <td style={{ textAlign: 'right', fontWeight: 600 }}>{f.entradas.toLocaleString('es-ES')}</td>
                    <td style={{ textAlign: 'right', fontWeight: 700, color: 'var(--burgundy)' }}>{euros(f.ingresos)}</td>
                    <td style={{ textAlign: 'right' }}>
                      <span className="admin-badge admin-badge-warning">
                        {ingresosTotales > 0 ? `${Math.round((f.ingresos / ingresosTotales) * 100)}%` : '—'}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Ocupación */}
      <div className="admin-panel" style={{ margin: 0 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '16px' }}>
          <h3 style={{ margin: 0, color: 'var(--burgundy)', fontSize: '1.2rem', fontWeight: 700 }}>Ocupación de salas</h3>
          <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            <select className="admin-select" value={cineId ?? ''} onChange={(e) => setCineElegido(e.target.value)} aria-label="Cine" style={{ minWidth: '220px' }}>
              {cines.length === 0 && <option value="">No hay cines</option>}
              {cines.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
            <select className="admin-select" value={diaSemana} onChange={(e) => setDiaSemana(e.target.value)} aria-label="Día de la semana">
              <option value="">Todos los días</option>
              {DIAS.slice(1).map((d, i) => <option key={d} value={i + 1}>{d}</option>)}
            </select>
          </div>
        </div>

        {ocupacion.error && ocupacion.clave === claveOcupacion && (
          <AvisoAdmin aviso={{ tipo: 'error', texto: `No se pudo cargar la ocupación: ${ocupacion.error}` }} />
        )}

        {cargandoOcupacion ? (
          <p style={{ color: 'var(--text-muted)' }}>Cargando ocupación…</p>
        ) : ocupacion.cine && (
          <>
            <p style={{ fontSize: '0.9rem', marginTop: 0 }}>
              Total del cine: <strong>{ocupacion.cine.totalTicketsSold}</strong> entradas vendidas sobre un aforo de{' '}
              <strong>{ocupacion.cine.totalCapacity}</strong> butacas ({ocupacion.cine.occupancyPercentage}%).
            </p>
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Sala</th>
                    <th>Tipo</th>
                    <th style={{ textAlign: 'right' }}>Aforo</th>
                    <th style={{ textAlign: 'right' }}>Entradas vendidas</th>
                    <th style={{ textAlign: 'right' }}>Ocupación</th>
                  </tr>
                </thead>
                <tbody>
                  {ocupacion.salas.length === 0 ? (
                    <tr>
                      <td colSpan="5" style={{ textAlign: 'center', padding: '24px', color: 'var(--text-muted)' }}>Este cine no tiene salas.</td>
                    </tr>
                  ) : ocupacion.salas.map(({ sala, metrica }) => (
                    <tr key={sala.id}>
                      <td><strong style={{ color: 'var(--burgundy)' }}>{sala.name}</strong></td>
                      <td>{TIPOS_SALA[sala.roomType] || sala.roomType}</td>
                      <td style={{ textAlign: 'right' }}>{metrica?.totalCapacity ?? sala.capacity}</td>
                      <td style={{ textAlign: 'right' }}>{metrica ? metrica.totalTicketsSold : '—'}</td>
                      <td style={{ textAlign: 'right' }}>
                        <span className="admin-badge admin-badge-warning">{metrica ? `${metrica.occupancyPercentage}%` : '—'}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
