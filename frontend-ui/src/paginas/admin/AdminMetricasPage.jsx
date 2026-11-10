import { useState } from 'react';
import peliculas from '../../assets/peliculas.json';

export default function AdminMetricasPage() {
  const [periodo, setPeriodo] = useState('semana');
  const [diaConcreto, setDiaConcreto] = useState(() => new Date().toISOString().split('T')[0]);

  // Cálculo ficticio según el periodo o día concreto
  const datos = (() => {
    if (periodo === 'dia-concreto') {
      const seed = diaConcreto.split('-').reduce((acc, v) => acc + parseInt(v, 10), 0);
      const ingresos = 3400 + (seed * 37) % 3200;
      const entradas = 350 + (seed * 11) % 400;
      return {
        ingresos: `${ingresos.toLocaleString('es-ES')} €`,
        entradas: entradas.toLocaleString('es-ES'),
        ticketMedio: `${(ingresos / entradas).toFixed(2)} €`,
        subtitulo: `Datos consolidados para el día ${diaConcreto}`,
      };
    }
    if (periodo === 'hoy') {
      return {
        ingresos: '4.820 €',
        entradas: '492',
        ticketMedio: '9,80 €',
        subtitulo: 'Datos en tiempo real de la jornada actual',
      };
    }
    if (periodo === 'semana') {
      return {
        ingresos: '38.450 €',
        entradas: '3.910',
        ticketMedio: '9,83 €',
        subtitulo: 'Últimos 7 días naturales',
      };
    }
    return {
      ingresos: '152.800 €',
      entradas: '15.420',
      ticketMedio: '9,91 €',
      subtitulo: 'Mes natural en curso',
    };
  })();

  return (
    <div className="admin-page-container">
      <header className="admin-header">
        <div>
          <h1 className="admin-header-title">Métricas de Rendimiento</h1>
          <p className="admin-header-desc">
            Facturación Box Office, venta de entradas y ticket medio de la red de cines.
          </p>
        </div>
      </header>

      {/* Selector de periodo con barrita inferior afin a la estética y selección de día concreto */}
      <div className="admin-tab-bar" style={{ justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', gap: '4px' }}>
          <button
            type="button"
            className={`admin-tab-btn${periodo === 'hoy' ? ' active' : ''}`}
            onClick={() => setPeriodo('hoy')}
          >
            Hoy
          </button>
          <button
            type="button"
            className={`admin-tab-btn${periodo === 'semana' ? ' active' : ''}`}
            onClick={() => setPeriodo('semana')}
          >
            Esta Semana
          </button>
          <button
            type="button"
            className={`admin-tab-btn${periodo === 'mes' ? ' active' : ''}`}
            onClick={() => setPeriodo('mes')}
          >
            Este Mes
          </button>
          <button
            type="button"
            className={`admin-tab-btn${periodo === 'dia-concreto' ? ' active' : ''}`}
            onClick={() => setPeriodo('dia-concreto')}
          >
            Día Concreto
          </button>
        </div>

        {periodo === 'dia-concreto' && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', paddingBottom: '6px' }}>
            <label className="admin-label" style={{ margin: 0, textTransform: 'none' }}>
              Fecha:
            </label>
            <input
              type="date"
              className="admin-input"
              value={diaConcreto}
              onChange={(e) => setDiaConcreto(e.target.value)}
              style={{ padding: '6px 10px', fontSize: '0.86rem', width: '160px' }}
            />
          </div>
        )}
      </div>

      <p style={{ margin: '-10px 0 20px', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
        {datos.subtitulo}
      </p>

      {/* Tarjetas KPI con estética sobria y rectangular */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
        gap: '18px',
        marginBottom: '28px'
      }}>
        <div className="admin-panel" style={{ margin: 0 }}>
          <span className="admin-label" style={{ color: 'var(--text-muted)' }}>
            Ingresos Box Office
          </span>
          <div style={{
            fontSize: '2.4rem',
            fontWeight: 800,
            color: 'var(--burgundy)',
            margin: '8px 0 4px',
            fontVariantNumeric: 'tabular-nums'
          }}>
            {datos.ingresos}
          </div>
          <span style={{ fontSize: '0.8rem', color: '#155724', fontWeight: 600 }}>
            +14% vs periodo equivalente
          </span>
        </div>

        <div className="admin-panel" style={{ margin: 0 }}>
          <span className="admin-label" style={{ color: 'var(--text-muted)' }}>
            Entradas Totales Vendidas
          </span>
          <div style={{
            fontSize: '2.4rem',
            fontWeight: 800,
            color: 'var(--burgundy)',
            margin: '8px 0 4px',
            fontVariantNumeric: 'tabular-nums'
          }}>
            {datos.entradas}
          </div>
          <span style={{ fontSize: '0.8rem', color: '#155724', fontWeight: 600 }}>
            Afluencia constante
          </span>
        </div>

        <div className="admin-panel" style={{ margin: 0 }}>
          <span className="admin-label" style={{ color: 'var(--text-muted)' }}>
            Precio Medio por Entrada (Ticket Medio)
          </span>
          <div style={{
            fontSize: '2.4rem',
            fontWeight: 800,
            color: 'var(--granate)',
            margin: '8px 0 4px',
            fontVariantNumeric: 'tabular-nums'
          }}>
            {datos.ticketMedio}
          </div>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Incluye formatos estándar, 3D, VIP e IMAX
          </span>
        </div>
      </div>

      {/* Desglose de recaudación por película */}
      <div className="admin-panel" style={{ margin: 0 }}>
        <h3 style={{ margin: '0 0 16px', color: 'var(--burgundy)', fontSize: '1.2rem', fontWeight: 700 }}>
          Recaudación por Título en Cartelera
        </h3>

        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Película</th>
                <th>Género</th>
                <th style={{ textAlign: 'right' }}>Entradas Vendidas</th>
                <th style={{ textAlign: 'right' }}>Ingresos Brutos (€)</th>
                <th style={{ textAlign: 'right' }}>Cuota Box Office</th>
              </tr>
            </thead>
            <tbody>
              {peliculas.slice(0, 6).map((peli, i) => {
                const share = [34, 26, 18, 11, 7, 4][i];
                const factor = periodo === 'hoy' ? 48 : periodo === 'semana' ? 384 : 1528;
                const rec = Math.round((factor * share));
                const ent = Math.round(rec / 9.8);

                return (
                  <tr key={peli.id}>
                    <td>
                      <strong style={{ color: 'var(--burgundy)' }}>{peli.title}</strong>
                    </td>
                    <td>
                      <span className="admin-badge admin-badge-dark">{peli.genre}</span>
                    </td>
                    <td style={{ textAlign: 'right', fontWeight: 600 }}>
                      {ent.toLocaleString('es-ES')}
                    </td>
                    <td style={{ textAlign: 'right', fontWeight: 700, color: 'var(--burgundy)' }}>
                      {rec.toLocaleString('es-ES')} €
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <span className="admin-badge admin-badge-warning">{share}%</span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
