import { useState, useEffect } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';

/* =========================================================
   Matriz de butacas personalizada por CINE y por SALA
   ========================================================= */
export function getSeatLayout(cineId, sala) {
  const c = Number(cineId) || 1;
  const s = Number(sala) || 1;

  if (s === 5 || (c <= 2 && s === 1)) {
    return {
      filas: 12,
      cols: 16,
      tipo: 'Sala IMAX Gran Pantalla',
      descripcion: 'Proyección Láser 4K y Sonido Inmersivo',
    };
  }

  if (s === 4) {
    return {
      filas: 6,
      cols: 8,
      tipo: 'Sala VIP Luxury',
      descripcion: 'Butacas reclinables y mayor espacio individual',
    };
  }

  if (s === 3) {
    const filas = 9 + (c % 2);
    const cols = 12 + (c % 3);
    return {
      filas,
      cols,
      tipo: 'Sala 3D RealD',
      descripcion: 'Proyección tridimensional de alta definición',
    };
  }

  const filas = 8 + ((c + s) % 3);
  const cols = 10 + ((c * 2 + s) % 4);
  return {
    filas,
    cols,
    tipo: `Sala ${s} Estándar`,
    descripcion: 'Sonido digital envolvente y butacas ergonómicas',
  };
}

export function getOcupadasSet(cineId, peliculaId, sala, hora) {
  const { filas, cols } = getSeatLayout(cineId, sala);
  const total = filas * cols;
  const horaNum = parseInt((hora || '16:00').replace(':', ''), 10);
  const seed = (Number(cineId) * 23 + Number(peliculaId) * 17 + Number(sala) * 11 + horaNum) % 10000;

  const ocupadas = new Set();
  let s = seed;
  const cantOcupadas = Math.floor(total * 0.28);
  while (ocupadas.size < cantOcupadas) {
    s = (s * 1664525 + 1013904223) % (2 ** 32);
    ocupadas.add(Math.abs(s) % total);
  }
  return ocupadas;
}

const PASOS = ['Sesión', 'Entradas', 'Butacas', 'Pago'];

function Stepper({ paso }) {
  return (
    <div className="compra-stepper" role="list">
      {PASOS.map((nombre, i) => (
        <div
          key={nombre}
          role="listitem"
          className={`compra-step${i < paso ? ' compra-step--done' : ''}${i === paso ? ' compra-step--active' : ''}`}
        >
          <div className="compra-step-circle">{i < paso ? '✓' : i + 1}</div>
          <span className="compra-step-label">{nombre}</span>
          {i < PASOS.length - 1 && <div className="compra-step-line" />}
        </div>
      ))}
    </div>
  );
}

function PasoResumen({ data, onNext }) {
  const fecha = new Date(data.fecha);
  const fechaStr = fecha.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
  const layout = getSeatLayout(data.cineId, data.sesion.sala);

  return (
    <div className="compra-panel">
      <h2 className="compra-panel-title">1. Resumen de tu sesión</h2>
      <p style={{ color: 'var(--text-muted)', marginBottom: '16px' }}>
        Verifica los datos seleccionados antes de continuar:
      </p>

      <div className="compra-resumen">
        <p><strong>Película:</strong> {data.peliculaTitle}</p>
        <p><strong>Cine:</strong> {data.cineName}</p>
        <p><strong>Fecha:</strong> <span style={{ textTransform: 'capitalize' }}>{fechaStr}</span></p>
        <p><strong>Horario:</strong> {data.sesion.hora}</p>
        <p><strong>Formato:</strong> {data.sesion.formato}</p>
        <p><strong>Sala asignada:</strong> {layout.tipo} ({layout.descripcion})</p>
        <p><strong>Precio unitario:</strong> {data.sesion.precio.toFixed(2)} €</p>
      </div>

      <div style={{ display: 'flex', gap: '14px', marginTop: '20px' }}>
        <button className="home-button home-button-dark" type="button" onClick={() => window.history.back()}>
          Cambiar sesión
        </button>
        <button className="home-button" type="button" onClick={onNext} style={{ flex: 1, justifyContent: 'center' }}>
          Continuar a selección de entradas →
        </button>
      </div>
    </div>
  );
}

function PasoEntradas({ precio, cantidad, setCantidad, onNext, onPrev }) {
  return (
    <div className="compra-panel">
      <h2 className="compra-panel-title">2. Número de entradas</h2>
      <p style={{ color: 'var(--text-muted)', marginBottom: '20px' }}>
        Indica cuántas personas asistirán a esta sesión (máximo 8 entradas por operación):
      </p>

      <div className="compra-entradas-selector">
        <button
          type="button"
          className="compra-cantidad-btn"
          onClick={() => setCantidad((n) => Math.max(1, n - 1))}
          disabled={cantidad <= 1}
        >
          −
        </button>
        <span className="compra-cantidad">{cantidad}</span>
        <button
          type="button"
          className="compra-cantidad-btn"
          onClick={() => setCantidad((n) => Math.min(8, n + 1))}
          disabled={cantidad >= 8}
        >
          +
        </button>
      </div>

      <p className="compra-subtotal">
        Importe subtotal: <strong>{(precio * cantidad).toFixed(2)} €</strong> ({precio.toFixed(2)} € × {cantidad})
      </p>

      <div className="compra-nav-btns" style={{ marginTop: '24px' }}>
        <button className="home-button home-button-dark" type="button" onClick={onPrev}>
          Volver
        </button>
        <button className="home-button" type="button" onClick={onNext}>
          Continuar a selección de butacas →
        </button>
      </div>
    </div>
  );
}

function PasoButacas({ data, cantidad, seleccionadas, setSeleccionadas, onNext, onPrev }) {
  const { cineId, sesion, peliculaId } = data;
  const layout = getSeatLayout(cineId, sesion.sala);
  const { filas, cols, tipo } = layout;
  const totalAsientos = filas * cols;
  const ocupadasSet = getOcupadasSet(cineId, peliculaId, sesion.sala, sesion.hora);

  function toggleSeat(idx) {
    if (ocupadasSet.has(idx)) return;
    setSeleccionadas((prev) => {
      if (prev.includes(idx)) return prev.filter((s) => s !== idx);
      if (prev.length >= cantidad) return prev;
      return [...prev, idx];
    });
  }

  const filasArr = Array.from({ length: filas }, (_, f) => f);
  const colsArr = Array.from({ length: cols }, (_, c) => c);

  return (
    <div className="compra-panel compra-panel--wide">
      <h2 className="compra-panel-title">
        3. Selección de butacas en sala
      </h2>

      <div style={{
        padding: '10px 16px',
        borderRadius: 'var(--radius-sm)',
        background: 'rgba(217, 164, 65, 0.1)',
        border: '1px solid rgba(217, 164, 65, 0.3)',
        fontSize: '0.9rem',
        color: 'var(--dark-burgundy)',
        marginBottom: '16px'
      }}>
        {data.cineName} · {tipo} ({filas} filas × {cols} columnas — Capacidad: {totalAsientos} asientos).
      </div>

      <p className="compra-butacas-hint">
        Has seleccionado <strong>{seleccionadas.length}</strong> de <strong>{cantidad}</strong> butaca{cantidad > 1 ? 's' : ''}.
        {seleccionadas.length < cantidad && ' Haz clic en las butacas libres que desees ocupar:'}
      </p>

      {/* Pantalla */}
      <div className="compra-pantalla">
        <div className="compra-pantalla-barra" />
        <span>PANTALLA DE PROYECCIÓN</span>
      </div>

      {/* Leyenda */}
      <div className="compra-leyenda">
        <span className="compra-leyenda-item"><span className="butaca butaca--libre" /> Libre</span>
        <span className="compra-leyenda-item"><span className="butaca butaca--ocupada" /> Ocupada</span>
        <span className="compra-leyenda-item"><span className="butaca butaca--elegida" /> Seleccionada</span>
      </div>

      {/* Mapa de butacas */}
      <div className="compra-mapa" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
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
              const elegida = seleccionadas.includes(idx);
              return (
                <button
                  key={idx}
                  type="button"
                  className={`butaca${ocupada ? ' butaca--ocupada' : elegida ? ' butaca--elegida' : ' butaca--libre'}`}
                  onClick={() => toggleSeat(idx)}
                  disabled={ocupada}
                  title={`Fila ${String.fromCharCode(65 + f)}, Asiento ${c + 1}${ocupada ? ' (ocupado)' : ''}`}
                  aria-label={`Fila ${String.fromCharCode(65 + f)} asiento ${c + 1}`}
                />
              );
            })}
          </div>
        ))}
      </div>

      <div className="compra-nav-btns" style={{ marginTop: '24px' }}>
        <button className="home-button home-button-dark" type="button" onClick={onPrev}>
          Volver
        </button>
        <button
          className="home-button"
          type="button"
          onClick={onNext}
          disabled={seleccionadas.length < cantidad}
        >
          {seleccionadas.length < cantidad
            ? `Selecciona ${cantidad - seleccionadas.length} butaca(s) más`
            : 'Proceder al pago →'}
        </button>
      </div>
    </div>
  );
}

function PasoPago({ data, cantidad, seleccionadas, onDone, onPrev }) {
  const [form, setForm] = useState({ titular: '', tarjeta: '', caducidad: '', cvv: '' });
  const [estado, setEstado] = useState('idle');
  const [pedidoId, setPedidoId] = useState('');

  const layout = getSeatLayout(data.cineId, data.sesion.sala);
  const total = (data.sesion.precio * cantidad).toFixed(2);

  const nombresButacas = seleccionadas.map((idx) => {
    const f = Math.floor(idx / layout.cols);
    const c = idx % layout.cols;
    return `${String.fromCharCode(65 + f)}${c + 1}`;
  });

  function formatTarjeta(val) {
    return val.replace(/\D/g, '').slice(0, 16).replace(/(\d{4})(?=\d)/g, '$1 ');
  }

  function handleSubmit(e) {
    e.preventDefault();
    setEstado('procesando');

    setTimeout(() => {
      const id = `CN-${Math.floor(100000 + Math.random() * 900000)}`;
      setPedidoId(id);
      setEstado('ok');

      try {
        const raw = localStorage.getItem('cinasee_entradas');
        const lista = raw ? JSON.parse(raw) : [];
        lista.unshift({
          id,
          pelicula: data.peliculaTitle,
          cine: data.cineName,
          fecha: data.fecha,
          hora: data.sesion.hora,
          sala: layout.tipo,
          butacas: nombresButacas,
          total,
          compradoEl: new Date().toISOString()
        });
        localStorage.setItem('cinasee_entradas', JSON.stringify(lista));
      } catch (err) {
        console.error('Error guardando ticket', err);
      }
    }, 1500);
  }

  if (estado === 'ok') {
    return (
      <div className="compra-panel compra-pago-ok">
        <div className="compra-pago-check">✓</div>
        <h2>Pago completado con éxito</h2>
        <p style={{ fontSize: '1.1rem' }}>Código de reserva: <strong style={{ color: 'var(--granate)' }}>{pedidoId}</strong></p>
        <div className="compra-resumen" style={{ textAlign: 'left', width: '100%', maxWidth: '520px' }}>
          <p><strong>Película:</strong> {data.peliculaTitle}</p>
          <p><strong>Cine:</strong> {data.cineName}</p>
          <p><strong>Sala:</strong> {layout.tipo}</p>
          <p><strong>Sesión:</strong> {data.sesion.hora} ({data.sesion.formato})</p>
          <p><strong>Butacas:</strong> {nombresButacas.join(', ')}</p>
          <p><strong>Importe total:</strong> {total} €</p>
        </div>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
          La reserva ha sido guardada en tu perfil. Puedes presentar tu código al entrar a la sala.
        </p>
        <div className="compra-nav-btns" style={{ width: '100%', maxWidth: '520px' }}>
          <button className="home-button" type="button" onClick={onDone} style={{ flex: 1, justifyContent: 'center' }}>
            Ver mis entradas
          </button>
          <Link className="home-button home-button-dark" to="/" style={{ flex: 1, justifyContent: 'center' }}>
            Volver al inicio
          </Link>
        </div>
      </div>
    );
  }

  if (estado === 'procesando') {
    return (
      <div className="compra-panel" style={{ textAlign: 'center', padding: '60px 20px' }}>
        <h2>Procesando pago de forma segura...</h2>
        <p style={{ color: 'var(--text-muted)' }}>Validando autorización bancaria. Por favor espera.</p>
      </div>
    );
  }

  return (
    <div className="compra-panel">
      <h2 className="compra-panel-title">4. Confirmación y pago</h2>

      <div className="compra-resumen compra-resumen--small" style={{ marginBottom: '16px' }}>
        <p><strong>{data.peliculaTitle}</strong> en {data.cineName}</p>
        <p>Sesión {data.sesion.hora} · {layout.tipo} · Butacas: <strong>{nombresButacas.join(', ')}</strong></p>
        <p className="compra-total-grande">Importe total: <strong>{total} €</strong></p>
      </div>

      <form className="compra-pago-form" onSubmit={handleSubmit} noValidate>
        <div className="login-field">
          <label htmlFor="pago-titular">Titular de la tarjeta</label>
          <input
            id="pago-titular"
            type="text"
            required
            placeholder="Nombre completo"
            value={form.titular}
            onChange={(e) => setForm((p) => ({ ...p, titular: e.target.value }))}
          />
        </div>

        <div className="login-field">
          <label htmlFor="pago-tarjeta">Número de tarjeta</label>
          <input
            id="pago-tarjeta"
            type="text"
            required
            placeholder="1234 5678 9012 3456"
            maxLength={19}
            value={form.tarjeta}
            onChange={(e) => setForm((p) => ({ ...p, tarjeta: formatTarjeta(e.target.value) }))}
          />
        </div>

        <div className="compra-pago-row">
          <div className="login-field">
            <label htmlFor="pago-cad">Caducidad (MM/AA)</label>
            <input
              id="pago-cad"
              type="text"
              required
              placeholder="MM/AA"
              maxLength={5}
              value={form.caducidad}
              onChange={(e) => {
                let val = e.target.value.replace(/\D/g, '').slice(0, 4);
                if (val.length > 2) val = val.slice(0, 2) + '/' + val.slice(2);
                setForm((p) => ({ ...p, caducidad: val }));
              }}
            />
          </div>
          <div className="login-field">
            <label htmlFor="pago-cvv">Código CVV</label>
            <input
              id="pago-cvv"
              type="password"
              required
              placeholder="123"
              maxLength={4}
              value={form.cvv}
              onChange={(e) => setForm((p) => ({ ...p, cvv: e.target.value.replace(/\D/g, '').slice(0, 4) }))}
            />
          </div>
        </div>

        <div className="compra-nav-btns" style={{ marginTop: '20px' }}>
          <button className="home-button home-button-dark" type="button" onClick={onPrev}>
            Modificar butacas
          </button>
          <button className="home-button" type="submit" style={{ flex: 1, justifyContent: 'center' }}>
            Abonar {total} €
          </button>
        </div>
      </form>
    </div>
  );
}

export default function CompraPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const data = location.state;

  const [paso, setPaso] = useState(0);
  const [cantidad, setCantidad] = useState(2);
  const [seleccionadas, setSeleccionadas] = useState([]);

  useEffect(() => {
    if (!data) navigate('/cartelera', { replace: true });
  }, [data, navigate]);

  if (!data) return null;

  function avanzar() {
    setPaso((p) => p + 1);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function retroceder() {
    setPaso((p) => Math.max(0, p - 1));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function irPerfil() {
    navigate('/perfil');
  }

  return (
    <div className="compra-page">
      <div className="compra-container">
        <Stepper paso={paso} />

        {paso === 0 && (
          <PasoResumen
            data={data}
            onNext={avanzar}
          />
        )}

        {paso === 1 && (
          <PasoEntradas
            precio={data.sesion.precio}
            cantidad={cantidad}
            setCantidad={setCantidad}
            onNext={() => { setSeleccionadas([]); avanzar(); }}
            onPrev={retroceder}
          />
        )}

        {paso === 2 && (
          <PasoButacas
            data={data}
            cantidad={cantidad}
            seleccionadas={seleccionadas}
            setSeleccionadas={setSeleccionadas}
            onNext={avanzar}
            onPrev={retroceder}
          />
        )}

        {paso === 3 && (
          <PasoPago
            data={data}
            cantidad={cantidad}
            seleccionadas={seleccionadas}
            onDone={irPerfil}
            onPrev={retroceder}
          />
        )}
      </div>
    </div>
  );
}
