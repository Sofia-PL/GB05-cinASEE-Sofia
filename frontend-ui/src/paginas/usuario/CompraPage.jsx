import { useState, useEffect, useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';
import { obtenerSala } from '../../api/cartelera';
import {
  obtenerButacasOcupadas,
  crearReserva,
  cancelarReserva,
  iniciarPagoRedsys,
  irARedsys,
  guardarPagoEnCurso,
  nombreButaca,
  DESCUENTOS,
  COMPRA_KEY,
} from '../../api/reservas';
import { layoutDeSala, butacaDeIndice, indicesOcupados } from '../../utils/butacas';
import MapaButacas from '../../components/MapaButacas';

/* ---------------------------------------------------------
   Compra de entradas
   - La sesión viene de Cartelera (Comp. 1): showId, sala y precio.
   - Las butacas, la reserva y el pago (Redsys) van contra Reservas (Comp. 2).
   - Reservas exige un token del Comp. 3: cliente registrado o invitado.
   --------------------------------------------------------- */
const SS_COMPRA_KEY = COMPRA_KEY;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
/** Máximo de butacas por reserva que admite Reservas */
const MAX_ENTRADAS = 10;

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
          <div className="compra-step-circle">{i + 1}</div>
          <span className="compra-step-label">{nombre}</span>
          {i < PASOS.length - 1 && <div className="compra-step-line" />}
        </div>
      ))}
    </div>
  );
}

/* Identificación: cliente con sesión iniciada o invitado con su correo */
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

function PasoResumen({ data, onNext, layout, identidadProps, listo, errorSala }) {
  const fechaStr = new Date(data.fecha).toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });

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
        {data.sesion.formato && <p><strong>Formato:</strong> {data.sesion.formato}</p>}
        <p>
          <strong>Sala:</strong>{' '}
          {layout ? `${layout.tipo} (${layout.descripcion})` : data.sesion.salaNombre || 'se muestra al identificarte'}
        </p>
        <p><strong>Precio unitario:</strong> {data.sesion.precio.toFixed(2)} €</p>
        <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
          Los descuentos (día del espectador o fidelización) se calculan al reservar las butacas.
        </p>
      </div>

      <BloqueIdentidad {...identidadProps} />
      {errorSala && <div className="cinasee-error" role="alert">{errorSala}</div>}

      <div style={{ display: 'flex', gap: '14px', marginTop: '20px' }}>
        <button className="home-button home-button-dark" type="button" onClick={() => window.history.back()}>
          Cambiar sesión
        </button>
        <button
          className="home-button"
          type="button"
          onClick={onNext}
          disabled={!listo}
          style={{ flex: 1, justifyContent: 'center' }}
        >
          Continuar a selección de entradas
        </button>
      </div>
    </div>
  );
}

/**
 * @param {number|null} libres butacas libres de la sesión (null mientras se consultan)
 */
function PasoEntradas({ precio, cantidad, setCantidad, libres, consultando, onNext, onPrev }) {
  // No se pueden pedir más entradas que butacas libres (ni más del máximo por operación).
  // Si no se pudieron consultar, se deja el máximo: Reservas lo vuelve a comprobar al reservar.
  const maximo = libres === null ? MAX_ENTRADAS : Math.min(MAX_ENTRADAS, libres);
  const agotada = libres === 0;

  return (
    <div className="compra-panel">
      <h2 className="compra-panel-title">2. Número de entradas</h2>
      <p style={{ color: 'var(--text-muted)', marginBottom: '20px' }}>
        Indica cuántas personas asistirán a esta sesión (máximo {maximo} {maximo === 1 ? 'entrada' : 'entradas'} por operación):
      </p>
      {libres !== null && !agotada && libres < MAX_ENTRADAS && (
        <div className="cinasee-aviso" role="status" style={{ marginBottom: '16px' }}>
          Solo {libres === 1 ? 'queda 1 butaca libre' : `quedan ${libres} butacas libres`} en esta sesión.
        </div>
      )}
      {agotada && (
        <div className="cinasee-error" role="alert" style={{ marginBottom: '16px' }}>
          Esta sesión está completa: no quedan butacas libres.
        </div>
      )}

      <div className="compra-entradas-selector">
        <button
          type="button"
          className="compra-cantidad-btn"
          onClick={() => setCantidad((n) => Math.max(1, n - 1))}
          disabled={cantidad <= 1 || agotada}
        >
          −
        </button>
        <span className="compra-cantidad">{cantidad}</span>
        <button
          type="button"
          className="compra-cantidad-btn"
          onClick={() => setCantidad((n) => Math.min(maximo, n + 1))}
          disabled={cantidad >= maximo || agotada}
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
        <button className="home-button" type="button" onClick={onNext} disabled={agotada || consultando}>
          {consultando ? 'Consultando butacas libres…' : 'Continuar a selección de butacas'}
        </button>
      </div>
    </div>
  );
}

function PasoButacas({ data, layout, ocupadas, cantidad, seleccionadas, setSeleccionadas, onNext, onPrev, procesando, errorReserva }) {
  const { filas, cols, tipo } = layout;

  function toggleSeat(idx) {
    if (!ocupadas || ocupadas.has(idx)) return;
    setSeleccionadas((prev) => {
      if (prev.includes(idx)) return prev.filter((s) => s !== idx);
      if (prev.length >= cantidad) return prev;
      return [...prev, idx];
    });
  }

  return (
    <div className="compra-panel compra-panel--wide">
      <h2 className="compra-panel-title">3. Selección de butacas en sala</h2>

      <div style={{
        padding: '10px 16px',
        borderRadius: 'var(--radius-sm)',
        background: 'rgba(217, 164, 65, 0.1)',
        border: '1px solid rgba(217, 164, 65, 0.3)',
        fontSize: '0.9rem',
        color: 'var(--dark-burgundy)',
        marginBottom: '16px',
      }}>
        {data.cineName}, {tipo} ({filas} filas × {cols} columnas, capacidad: {filas * cols} asientos).
      </div>

      <p className="compra-butacas-hint">
        Has seleccionado <strong>{seleccionadas.length}</strong> de <strong>{cantidad}</strong> butaca{cantidad > 1 ? 's' : ''}.
        {seleccionadas.length < cantidad && ' Haz clic en las butacas libres que desees ocupar:'}
      </p>

      {ocupadas ? (
        <MapaButacas layout={layout} ocupadas={ocupadas} seleccionadas={seleccionadas} onToggle={toggleSeat} />
      ) : (
        !errorReserva && <p style={{ color: 'var(--text-muted)' }}>Consultando las butacas libres…</p>
      )}

      {errorReserva && <div className="cinasee-error" role="alert" style={{ marginTop: '20px' }}>{errorReserva}</div>}

      <div className="compra-nav-btns" style={{ marginTop: '24px' }}>
        <button className="home-button home-button-dark" type="button" onClick={onPrev} disabled={procesando}>
          Volver
        </button>
        <button
          className="home-button"
          type="button"
          onClick={onNext}
          disabled={seleccionadas.length < cantidad || procesando}
        >
          {procesando
            ? 'Bloqueando butacas…'
            : seleccionadas.length < cantidad
              ? `Selecciona ${cantidad - seleccionadas.length} butaca(s) más`
              : 'Reservar butacas y pagar'}
        </button>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------
   Paso 4: las butacas ya están bloqueadas en Reservas;
   se paga en la web de Redsys.
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
        invitado: Boolean(reserva.guestEmail),
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
        <p>Sesión {data.sesion.hora}, {layout.tipo}. Butacas: <strong>{butacas.join(', ')}</strong></p>
        <p>Precio por entrada: {formatoEuros(reserva.unitPrice)} €{descuento && `, ${descuento}`}</p>
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
          <strong>Entorno de pruebas de Redsys (no se cobra dinero real).</strong> Para pagar solo necesitas
          el número de esta tarjeta de prueba:
          <ul>
            <li>Número: <code>4548 8120 4940 0004</code></li>
            <li>El resto de datos (caducidad, CVV…) pueden ser cualquiera: no se comprueban.</li>
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
  if (state?.sesion?.showId) {
    try {
      sessionStorage.setItem(SS_COMPRA_KEY, JSON.stringify(state));
    } catch {
      // ignorar
    }
    return state;
  }
  try {
    const guardado = JSON.parse(sessionStorage.getItem(SS_COMPRA_KEY) || 'null');
    return guardado?.sesion?.showId ? guardado : null;
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

  const tokenCliente = tokenUsuario();
  const [tokenInvitado, setTokenInvitado] = useState(null);   // solo tras pulsar "Continuar como invitado"
  const token = tokenCliente || tokenInvitado;
  const [guestEmail, setGuestEmail] = useState(user?.email || '');
  const [identidadError, setIdentidadError] = useState('');
  const [conectando, setConectando] = useState(false);
  const [sala, setSala] = useState({ layout: null, error: '' });
  const [ocupadas, setOcupadas] = useState(null);
  const [reserva, setReserva] = useState(null);
  const [procesando, setProcesando] = useState(false);
  const [errorReserva, setErrorReserva] = useState('');

  const identidad = tokenCliente ? 'cliente' : tokenInvitado ? 'invitado' : 'ninguna';
  const esInvitado = identidad === 'invitado';
  const layout = sala.layout;

  useEffect(() => {
    if (!data) navigate('/cartelera', { replace: true });
  }, [data, navigate]);

  // Con el token ya disponible se consulta la sala (filas y columnas) en Cartelera
  useEffect(() => {
    if (!data || !token) return undefined;
    let cancelado = false;
    obtenerSala(data.sesion.roomId, token)
      .then((room) => { if (!cancelado) setSala({ layout: layoutDeSala(room), error: '' }); })
      .catch((err) => { if (!cancelado) setSala({ layout: null, error: `No se pudo cargar la sala: ${err.message}` }); });
    return () => { cancelado = true; };
  }, [token, data]);

  // Butacas ocupadas en Reservas (se consultan al elegir el número de entradas y otra vez en el paso de butacas)
  const cargarOcupadas = useCallback(async () => {
    if (!layout) return;
    try {
      const mapa = await obtenerButacasOcupadas(data.sesion.showId);
      const set = indicesOcupados(mapa.occupiedSeats, layout.cols);
      setOcupadas(set);
      // Si se habían pedido más entradas de las que quedan libres, se ajusta
      const libresAhora = layout.filas * layout.cols - set.size;
      setCantidad((n) => Math.max(1, Math.min(n, libresAhora)));
    } catch (err) {
      setErrorReserva(`No se pudo consultar el mapa de butacas: ${err.message}`);
    }
  }, [layout, data]);

  const libres = layout && ocupadas ? layout.filas * layout.cols - ocupadas.size : null;

  if (!data) return null;

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

  /** Paso 3 -> 4: bloquea las butacas creando la reserva */
  async function reservarButacas() {
    setErrorReserva('');
    setProcesando(true);
    try {
      const seats = seleccionadas.map((idx) => butacaDeIndice(idx, layout.cols));
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

  return (
    <div className="compra-page">
      <div className="compra-container">
        <Stepper paso={paso} />

        {paso === 0 && (
          <PasoResumen
            data={data}
            onNext={() => { setOcupadas(null); setErrorReserva(''); avanzar(); cargarOcupadas(); }}
            layout={layout}
            listo={Boolean(token && layout)}
            errorSala={sala.error}
            identidadProps={{
              identidad,
              guestEmail,
              setGuestEmail,
              onInvitado: continuarComoInvitado,
              onLogin: irALogin,
              error: identidadError,
              cargando: conectando,
            }}
          />
        )}

        {paso === 1 && (
          <PasoEntradas
            precio={data.sesion.precio}
            cantidad={cantidad}
            setCantidad={setCantidad}
            libres={libres}
            consultando={ocupadas === null && !errorReserva}
            onNext={() => { setSeleccionadas([]); setOcupadas(null); setErrorReserva(''); avanzar(); cargarOcupadas(); }}
            onPrev={retroceder}
          />
        )}

        {paso === 2 && layout && (
          <PasoButacas
            data={data}
            layout={layout}
            ocupadas={ocupadas}
            cantidad={cantidad}
            seleccionadas={seleccionadas}
            setSeleccionadas={setSeleccionadas}
            onNext={reservarButacas}
            onPrev={() => { setErrorReserva(''); retroceder(); }}
            procesando={procesando}
            errorReserva={errorReserva}
          />
        )}

        {paso === 3 && reserva && (
          <PasoPagoRedsys
            data={data}
            reserva={reserva}
            layout={layout}
            token={token}
            onPrev={volverDelPago}
          />
        )}
      </div>
    </div>
  );
}
