import { useState, useEffect, useCallback } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';
import { getSeatLayout, getOcupadasSet } from '../../utils/butacas';
import { obtenerSala, TIPOS_SALA } from '../../api/cartelera';
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

/* ---------------------------------------------------------
   Compra REAL vs compra de DEMOSTRACIÓN
   - Real: la sesión viene de la Cartelera (Comp. 1) y trae showId.
     Las butacas, la reserva y el pago (Redsys) van contra Reservas (Comp. 2).
   - Demostración: sesiones inventadas, mapa pseudoaleatorio y pago simulado.
   --------------------------------------------------------- */
const SS_COMPRA_KEY = 'cinasee_compra';
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Layout de una sala real de Cartelera con el mismo formato que getSeatLayout */
function layoutDeSala(room) {
  const formato = TIPOS_SALA[room.roomType] || room.roomType || '';
  return {
    filas: room.rowsCount,
    cols: room.columnsCount,
    tipo: room.name || `Sala ${room.id}`,
    descripcion: [formato && `Formato ${formato}`, room.wheelchairAccessible && 'accesible en silla de ruedas']
      .filter(Boolean)
      .join(' · ') || 'Sala de proyección',
  };
}

function formatoEuros(valor) {
  return Number(valor || 0).toFixed(2);
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

/* ---------------------------------------------------------
   Identificación para la compra real: Reservas exige un token
   del Comp. 3 (cliente registrado o invitado con su correo).
   --------------------------------------------------------- */
function BloqueIdentidad({ identidad, guestEmail, setGuestEmail, onInvitado, onLogin, error, cargando }) {
  if (identidad === 'cliente') {
    return (
      <div className="cinasee-aviso cinasee-aviso--ok">
        Has iniciado sesión: la compra se asociará a tu cuenta y sumará puntos de fidelización.
      </div>
    );
  }
  if (identidad === 'invitado') {
    return (
      <div className="cinasee-aviso cinasee-aviso--ok">
        Compra como invitado. Te identificamos con el correo <strong>{guestEmail}</strong>.
      </div>
    );
  }

  return (
    <div className="compra-identidad">
      <h3 className="compra-identidad-title">¿Cómo quieres comprar?</h3>
      {identidad === 'demo' && (
        <p className="compra-identidad-texto">
          Has entrado con un perfil de prueba (acceso rápido), que no existe en el servidor.
          Inicia sesión con una cuenta real o continúa como invitado.
        </p>
      )}
      <div className="compra-identidad-opciones">
        <div className="compra-identidad-opcion">
          <p className="compra-identidad-texto">Con tu cuenta CinASEE acumulas puntos y ves tus entradas en tu perfil.</p>
          <button className="home-button" type="button" onClick={onLogin}>
            Iniciar sesión
          </button>
        </div>
        <form
          className="compra-identidad-opcion"
          onSubmit={(e) => { e.preventDefault(); onInvitado(); }}
          noValidate
        >
          <div className="login-field">
            <label htmlFor="compra-guest-email">Comprar como invitado</label>
            <input
              id="compra-guest-email"
              type="email"
              placeholder="tu.correo@email.com"
              value={guestEmail}
              onChange={(e) => setGuestEmail(e.target.value)}
            />
          </div>
          <button className="home-button home-button-dark" type="submit" disabled={cargando}>
            {cargando ? 'Conectando…' : 'Continuar como invitado'}
          </button>
        </form>
      </div>
      {error && <div className="cinasee-error" role="alert">{error}</div>}
    </div>
  );
}

function PasoResumen({ data, onNext, layoutReal, identidadProps, listo, errorSala }) {
  const fecha = new Date(data.fecha);
  const fechaStr = fecha.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
  const layout = layoutReal || getSeatLayout(data.cineId, data.sesion.sala);
  const esReal = Boolean(identidadProps);

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
        {data.sesion.formato &&<p><strong>Formato:</strong> {data.sesion.formato}</p>}
        <p>
          <strong>Sala asignada:</strong>{' '}
          {esReal && !layoutReal
            ? data.sesion.salaNombre || 'se muestra al identificarte'
            : `${layout.tipo} (${layout.descripcion})`}
        </p>
        <p><strong>Precio unitario:</strong> {data.sesion.precio.toFixed(2)} €</p>
        {esReal && (
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            Los descuentos (día del espectador o fidelización) se calculan al reservar las butacas.
          </p>
        )}
      </div>

      {esReal && <BloqueIdentidad {...identidadProps} />}
      {errorSala && <div className="cinasee-error" role="alert">{errorSala}</div>}

      <div style={{ display: 'flex', gap: '14px', marginTop: '20px' }}>
        <button className="home-button home-button-dark" type="button" onClick={() => window.history.back()}>
          Cambiar sesión
        </button>
        <button
          className="home-button"
          type="button"
          onClick={onNext}
          disabled={esReal && !listo}
          style={{ flex: 1, justifyContent: 'center' }}
        >
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

function PasoButacas({
  data, cantidad, seleccionadas, setSeleccionadas, onNext, onPrev,
  layoutReal, ocupadasReal, procesando, errorReserva,
}) {
  const { cineId, sesion, peliculaId } = data;
  const esReal = Boolean(layoutReal);
  const layout = layoutReal || getSeatLayout(cineId, sesion.sala);
  const { filas, cols, tipo } = layout;
  const totalAsientos = filas * cols;
  // Real: butacas ocupadas (bloqueadas o vendidas) que devuelve Reservas
  const ocupadasSet = esReal
    ? ocupadasReal || new Set()
    : getOcupadasSet(cineId, peliculaId, sesion.sala, sesion.hora);

  function toggleSeat(idx) {
    if (ocupadasSet.has(idx)) return;
    setSeleccionadas((prev) => {
      if (prev.includes(idx)) return prev.filter((s) => s !== idx);
      if (prev.length >= cantidad) return prev;
      return [...prev, idx];
    });
  }

  function handleContinuar() {
    if (seleccionadas.length !== cantidad || procesando) return;
    onNext();
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

      {errorReserva && <div className="cinasee-error" role="alert" style={{ marginTop: '20px' }}>{errorReserva}</div>}

      <div className="compra-nav-btns" style={{ marginTop: '24px' }}>
        <button className="home-button home-button-dark" type="button" onClick={onPrev} disabled={procesando}>
          Volver
        </button>
        <button
          className="home-button"
          type="button"
          onClick={handleContinuar}
          disabled={seleccionadas.length < cantidad || procesando}
        >
          {procesando
            ? 'Bloqueando butacas…'
            : seleccionadas.length < cantidad
              ? `Selecciona ${cantidad - seleccionadas.length} butaca(s) más`
              : esReal ? 'Reservar butacas y pagar →' : 'Proceder al pago →'}
        </button>
      </div>
    </div>
  );
}

function PasoPago({ data, cantidad, seleccionadas, onDone, onPrev }) {
  const [form, setForm] = useState({ titular: '', tarjeta: '', caducidad: '', cvv: '' });
  const [estado, setEstado] = useState('idle');
  const [pedidoId, setPedidoId] = useState('');
  const [errores, setErrores] = useState({});
  const [errorGlobal, setErrorGlobal] = useState('');

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

  function validarFormulario() {
    const nuevos = {};
    const titularTrim = (form.titular || '').trim();
    const tarjetaRaw = (form.tarjeta || '').replace(/\s+/g, '');
    const caducidadTrim = (form.caducidad || '').trim();
    const cvvTrim = (form.cvv || '').trim();

    // 1. Titular
    if (!titularTrim) {
      nuevos.titular = 'Introduce el nombre y apellidos del titular de la tarjeta.';
    } else if (titularTrim.length < 3) {
      nuevos.titular = 'El nombre del titular debe tener al menos 3 caracteres.';
    }

    // 2. Tarjeta (16 dígitos numéricos)
    if (!tarjetaRaw) {
      nuevos.tarjeta = 'Introduce el número de tu tarjeta bancaria.';
    } else if (tarjetaRaw.length !== 16 || !/^\d{16}$/.test(tarjetaRaw)) {
      nuevos.tarjeta = `El número de tarjeta debe tener 16 dígitos (has introducido ${tarjetaRaw.length}).`;
    }

    // 3. Caducidad (MM/AA)
    if (!caducidadTrim) {
      nuevos.caducidad = 'Introduce la fecha de caducidad (MM/AA).';
    } else if (!/^(0[1-9]|1[0-2])\/\d{2}$/.test(caducidadTrim)) {
      nuevos.caducidad = 'Formato no válido. Debe ser MM/AA (ej: 08/28).';
    } else {
      const [mmStr, aaStr] = caducidadTrim.split('/');
      const mes = parseInt(mmStr, 10);
      const ano = 2000 + parseInt(aaStr, 10);
      const ahora = new Date();
      const anoActual = ahora.getFullYear();
      const mesActual = ahora.getMonth() + 1;

      if (ano < anoActual || (ano === anoActual && mes < mesActual)) {
        nuevos.caducidad = 'La tarjeta introducida está caducada.';
      }
    }

    // 4. CVV (3 o 4 dígitos)
    if (!cvvTrim) {
      nuevos.cvv = 'Introduce el código de seguridad CVV.';
    } else if (!/^\d{3,4}$/.test(cvvTrim)) {
      nuevos.cvv = 'El código CVV debe tener 3 o 4 dígitos.';
    }

    setErrores(nuevos);
    if (Object.keys(nuevos).length > 0) {
      setErrorGlobal('Por favor, completa y corrige los campos de pago en rojo.');
      return false;
    }

    setErrorGlobal('');
    return true;
  }

  function handleSubmit(e) {
    e.preventDefault();

    if (!validarFormulario()) {
      return;
    }

    setEstado('procesando');

    setTimeout(() => {
      const idGenerado = `CN-${Math.floor(100000 + Math.random() * 900000)}`;
      setPedidoId(idGenerado);
      setEstado('ok');

      try {
        const raw = localStorage.getItem('cinasee_entradas');
        const lista = raw ? JSON.parse(raw) : [];
        lista.unshift({
          id: idGenerado,
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
    }, 1200);
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
        <p style={{ color: 'var(--text-muted)' }}>Validando autorización bancaria y emitiendo entradas. Por favor espera.</p>
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

      {errorGlobal && (
        <div
          role="alert"
          style={{
            padding: '10px 14px',
            borderRadius: '6px',
            background: '#fde8e8',
            border: '1px solid #f8b4b4',
            color: '#9b1c1c',
            marginBottom: '16px',
            fontSize: '0.88rem',
            fontWeight: 600
          }}
        >
          {errorGlobal}
        </div>
      )}

      <form className="compra-pago-form" onSubmit={handleSubmit} noValidate>
        <div className="login-field">
          <label htmlFor="pago-titular">Titular de la tarjeta</label>
          <input
            id="pago-titular"
            type="text"
            placeholder="Nombre completo tal como figura en la tarjeta"
            value={form.titular}
            onChange={(e) => {
              setForm((p) => ({ ...p, titular: e.target.value }));
              if (errores.titular) setErrores((prev) => ({ ...prev, titular: '' }));
            }}
            style={errores.titular ? { borderColor: '#d9383a', background: '#fff5f5' } : {}}
          />
          {errores.titular && (
            <span style={{ color: '#d9383a', fontSize: '0.8rem', marginTop: '4px' }}>
              {errores.titular}
            </span>
          )}
        </div>

        <div className="login-field">
          <label htmlFor="pago-tarjeta">Número de tarjeta (16 dígitos)</label>
          <input
            id="pago-tarjeta"
            type="text"
            placeholder="1234 5678 9012 3456"
            maxLength={19}
            value={form.tarjeta}
            onChange={(e) => {
              setForm((p) => ({ ...p, tarjeta: formatTarjeta(e.target.value) }));
              if (errores.tarjeta) setErrores((prev) => ({ ...prev, tarjeta: '' }));
            }}
            style={errores.tarjeta ? { borderColor: '#d9383a', background: '#fff5f5' } : {}}
          />
          {errores.tarjeta && (
            <span style={{ color: '#d9383a', fontSize: '0.8rem', marginTop: '4px' }}>
              {errores.tarjeta}
            </span>
          )}
        </div>

        <div className="compra-pago-row">
          <div className="login-field">
            <label htmlFor="pago-cad">Caducidad (MM/AA)</label>
            <input
              id="pago-cad"
              type="text"
              placeholder="MM/AA"
              maxLength={5}
              value={form.caducidad}
              onChange={(e) => {
                let val = e.target.value.replace(/\D/g, '').slice(0, 4);
                if (val.length > 2) val = val.slice(0, 2) + '/' + val.slice(2);
                setForm((p) => ({ ...p, caducidad: val }));
                if (errores.caducidad) setErrores((prev) => ({ ...prev, caducidad: '' }));
              }}
              style={errores.caducidad ? { borderColor: '#d9383a', background: '#fff5f5' } : {}}
            />
            {errores.caducidad && (
              <span style={{ color: '#d9383a', fontSize: '0.8rem', marginTop: '4px' }}>
                {errores.caducidad}
              </span>
            )}
          </div>

          <div className="login-field">
            <label htmlFor="pago-cvv">Código CVV</label>
            <input
              id="pago-cvv"
              type="password"
              placeholder="123"
              maxLength={4}
              value={form.cvv}
              onChange={(e) => {
                setForm((p) => ({ ...p, cvv: e.target.value.replace(/\D/g, '').slice(0, 4) }));
                if (errores.cvv) setErrores((prev) => ({ ...prev, cvv: '' }));
              }}
              style={errores.cvv ? { borderColor: '#d9383a', background: '#fff5f5' } : {}}
            />
            {errores.cvv && (
              <span style={{ color: '#d9383a', fontSize: '0.8rem', marginTop: '4px' }}>
                {errores.cvv}
              </span>
            )}
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

/* ---------------------------------------------------------
   Paso 4 (compra real): las butacas ya están bloqueadas en
   Reservas; se paga en la web de pruebas de Redsys.
   --------------------------------------------------------- */
function useCuentaAtras(hasta) {
  const [ahora, setAhora] = useState(() => Date.now());
  useEffect(() => {
    const t = window.setInterval(() => setAhora(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, []);
  const restante = Math.max(0, new Date(hasta).getTime() - ahora);
  return {
    restante,
    texto: `${Math.floor(restante / 60000)}:${String(Math.floor((restante % 60000) / 1000)).padStart(2, '0')}`,
  };
}

function PasoPagoRedsys({ data, reserva, layout, token, onPrev }) {
  const [estado, setEstado] = useState('idle');
  const [error, setError] = useState('');
  const { restante, texto } = useCuentaAtras(reserva.expiresAt);
  const caducada = restante === 0;

  const butacas = (reserva.seats || []).map((s) => nombreButaca(s.row, s.column));
  const descuento = DESCUENTOS[reserva.discountApplied] || '';

  async function handlePagar() {
    setError('');
    setEstado('redirigiendo');
    try {
      const formulario = await iniciarPagoRedsys(reserva.id, token);
      // Lo que necesita la página de vuelta para confirmar el pago y mostrar las entradas
      guardarPagoEnCurso({
        reservationId: reserva.id,
        token,
        pelicula: data.peliculaTitle,
        cine: data.cineName,
        sala: layout.tipo,
        fecha: data.fecha,
        hora: data.sesion.hora,
        butacas,
        total: formatoEuros(reserva.totalAmount),
      });
      irARedsys(formulario);
    } catch (err) {
      setEstado('idle');
      setError(err.message);
    }
  }

  if (estado === 'redirigiendo') {
    return (
      <div className="compra-panel" style={{ textAlign: 'center', padding: '60px 20px' }}>
        <h2>Conectando con la pasarela segura de Redsys…</h2>
        <p style={{ color: 'var(--text-muted)' }}>En unos segundos se abrirá la página de pago. No cierres esta ventana.</p>
      </div>
    );
  }

  return (
    <div className="compra-panel">
      <h2 className="compra-panel-title">4. Confirmación y pago</h2>

      <div className="compra-resumen compra-resumen--small" style={{ marginBottom: '16px' }}>
        <p><strong>{data.peliculaTitle}</strong> en {data.cineName}</p>
        <p>Sesión {data.sesion.hora} · {layout.tipo} · Butacas: <strong>{butacas.join(', ')}</strong></p>
        <p>Precio por entrada: {formatoEuros(reserva.unitPrice)} €{descuento && ` · ${descuento}`}</p>
        <p className="compra-total-grande">Importe total: <strong>{formatoEuros(reserva.totalAmount)} €</strong></p>
      </div>

      <div className={`cinasee-aviso${caducada ? ' cinasee-aviso--error' : ''}`}>
        {caducada
          ? 'El tiempo de reserva ha terminado y las butacas se han liberado. Vuelve a elegirlas.'
          : <>Tus butacas están reservadas durante <strong>{texto}</strong> minutos. Completa el pago antes de que termine el tiempo.</>}
      </div>

      <div className="compra-redsys">
        <p className="compra-redsys-title">Pago seguro con Redsys</p>
        <p className="compra-redsys-texto">
          Al pulsar el botón irás a la página de pago de Redsys, donde introducirás la tarjeta.
          CinASEE no recibe ni guarda los datos de tu tarjeta.
        </p>
        <div className="compra-redsys-test">
          <strong>Entorno de pruebas (no se cobra dinero real).</strong> Usa la tarjeta de prueba de Redsys:
          <ul>
            <li>Número: <code>4548 8120 4940 0004</code></li>
            <li>Caducidad: <code>12/49</code> (cualquier fecha futura) · CVV: <code>123</code></li>
            <li>Código de autenticación (CIP): <code>123456</code></li>
          </ul>
        </div>
      </div>

      {error && <div className="cinasee-error" role="alert">{error}</div>}

      <div className="compra-nav-btns" style={{ marginTop: '20px' }}>
        <button className="home-button home-button-dark" type="button" onClick={onPrev}>
          Modificar butacas
        </button>
        <button
          className="home-button"
          type="button"
          onClick={handlePagar}
          disabled={caducada}
          style={{ flex: 1, justifyContent: 'center' }}
        >
          Pagar {formatoEuros(reserva.totalAmount)} € con Redsys
        </button>
      </div>
    </div>
  );
}

/** Datos de la sesión elegida: del estado de la navegación o, si se recarga o se vuelve del login, de sessionStorage */
function leerDatosCompra(state) {
  if (state?.sesion) {
    try {
      sessionStorage.setItem(SS_COMPRA_KEY, JSON.stringify(state));
    } catch {
      // ignorar
    }
    return state;
  }
  try {
    return JSON.parse(sessionStorage.getItem(SS_COMPRA_KEY) || 'null');
  } catch {
    return null;
  }
}

export default function CompraPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [data] = useState(() => leerDatosCompra(location.state));
  const { user, tokenUsuario, obtenerTokenInvitado } = useAuth();

  const [paso, setPaso] = useState(0);
  const [cantidad, setCantidad] = useState(2);
  const [seleccionadas, setSeleccionadas] = useState([]);

  // ---- Estado de la compra real (Reservas + Redsys) ----
  const esReal = Boolean(data?.sesion?.showId);
  const tokenCliente = tokenUsuario();
  const [tokenInvitado, setTokenInvitado] = useState(null);   // solo tras pulsar "Continuar como invitado"
  const token = tokenCliente || tokenInvitado;
  const [guestEmail, setGuestEmail] = useState(user?.demo ? '' : user?.email || '');
  const [identidadError, setIdentidadError] = useState('');
  const [conectando, setConectando] = useState(false);
  const [layoutReal, setLayoutReal] = useState(null);
  const [errorSala, setErrorSala] = useState('');
  const [ocupadasReal, setOcupadasReal] = useState(null);
  const [reserva, setReserva] = useState(null);
  const [procesando, setProcesando] = useState(false);
  const [errorReserva, setErrorReserva] = useState('');

  const identidad = !esReal
    ? null
    : tokenCliente
      ? 'cliente'
      : tokenInvitado
        ? 'invitado'
        : user?.demo
          ? 'demo'
          : 'ninguna';
  const esInvitado = identidad === 'invitado';

  useEffect(() => {
    if (!data || !data.sesion) {
      navigate('/cartelera', { replace: true });
    }
  }, [data, navigate]);

  // Con el token ya disponible se consulta la sala real (filas y columnas) en Cartelera
  useEffect(() => {
    if (!esReal || !token || !(identidad === 'cliente' || identidad === 'invitado')) return;
    let cancelado = false;
    obtenerSala(data.sesion.roomId, token)
      .then((room) => {
        if (cancelado) return;
        setErrorSala('');
        setLayoutReal(layoutDeSala(room));
      })
      .catch((err) => { if (!cancelado) setErrorSala(`No se pudo cargar la sala: ${err.message}`); });
    return () => { cancelado = true; };
  }, [esReal, token, identidad, data]);

  // Butacas ocupadas en Reservas (se consultan al entrar en el paso de butacas)
  const cargarOcupadas = useCallback(async () => {
    if (!esReal || !layoutReal) return;
    try {
      const mapa = await obtenerButacasOcupadas(data.sesion.showId);
      setOcupadasReal(new Set(
        (mapa.occupiedSeats || []).map((s) => (s.row - 1) * layoutReal.cols + (s.column - 1))
      ));
    } catch (err) {
      setErrorReserva(`No se pudo consultar el mapa de butacas: ${err.message}`);
    }
  }, [esReal, layoutReal, data]);

  if (!data || !data.sesion) return null;

  function avanzar() {
    setPaso((p) => p + 1);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function retroceder() {
    setPaso((p) => Math.max(0, p - 1));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function continuarComoInvitado() {
    setIdentidadError('');
    if (!EMAIL_REGEX.test(guestEmail.trim())) {
      setIdentidadError('Introduce un correo electrónico válido para recibir tus entradas.');
      return;
    }
    setConectando(true);
    try {
      setGuestEmail(guestEmail.trim());
      setTokenInvitado(await obtenerTokenInvitado());
    } catch (err) {
      setIdentidadError(err.message);
    } finally {
      setConectando(false);
    }
  }

  function irALogin() {
    navigate('/login', { state: { from: '/compra' } });
  }

  /** Paso 3 -> 4 en la compra real: bloquea las butacas creando la reserva */
  async function reservarButacas() {
    setErrorReserva('');
    setProcesando(true);
    try {
      const seats = seleccionadas.map((idx) => ({
        row: Math.floor(idx / layoutReal.cols) + 1,
        column: (idx % layoutReal.cols) + 1,
      }));
      const nueva = await crearReserva(
        { showId: data.sesion.showId, seats, ...(esInvitado ? { guestEmail } : {}) },
        token
      );
      setReserva(nueva);
      avanzar();
    } catch (err) {
      if (err.code === 'SEATS_UNAVAILABLE' || err.status === 409) {
        const quitadas = err.details?.length
          ? ` (${err.details.map((d) => {
            const [r, c] = d.split('-').map(Number);
            return r && c ? nombreButaca(r, c) : d;
          }).join(', ')})`
          : '';
        setErrorReserva(`${err.message}${quitadas}. Elige otras butacas.`);
        setSeleccionadas([]);
        cargarOcupadas();
      } else {
        setErrorReserva(err.message);
      }
    } finally {
      setProcesando(false);
    }
  }

  /** Volver desde el pago: se cancela la reserva para liberar las butacas */
  async function volverDelPago() {
    if (reserva) {
      try {
        await cancelarReserva(reserva.id, token);
      } catch {
        // si ya caducó o no se puede cancelar, las butacas se liberan solas al expirar
      }
      setReserva(null);
    }
    setSeleccionadas([]);
    retroceder();
    cargarOcupadas();
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
            layoutReal={layoutReal}
            listo={Boolean(token && layoutReal)}
            errorSala={errorSala}
            identidadProps={esReal ? {
              identidad,
              guestEmail,
              setGuestEmail,
              onInvitado: continuarComoInvitado,
              onLogin: irALogin,
              error: identidadError,
              cargando: conectando,
            } : null}
          />
        )}

        {paso === 1 && (
          <PasoEntradas
            precio={data.sesion.precio}
            cantidad={cantidad}
            setCantidad={setCantidad}
            onNext={() => { setSeleccionadas([]); avanzar(); cargarOcupadas(); }}
            onPrev={retroceder}
          />
        )}

        {paso === 2 && (
          <PasoButacas
            data={data}
            cantidad={cantidad}
            seleccionadas={seleccionadas}
            setSeleccionadas={setSeleccionadas}
            onNext={esReal ? reservarButacas : avanzar}
            onPrev={() => { setErrorReserva(''); retroceder(); }}
            layoutReal={esReal ? layoutReal : null}
            ocupadasReal={ocupadasReal}
            procesando={procesando}
            errorReserva={errorReserva}
          />
        )}

        {paso === 3 && esReal && reserva && (
          <PasoPagoRedsys
            data={data}
            reserva={reserva}
            layout={layoutReal}
            token={token}
            onPrev={volverDelPago}
          />
        )}

        {paso === 3 && !esReal && (
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
