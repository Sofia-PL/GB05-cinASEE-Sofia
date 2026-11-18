import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';
import {
  confirmarPagoRedsys,
  iniciarPagoRedsys,
  cancelarReserva,
  irARedsys,
  leerPagoEnCurso,
  guardarPagoEnCurso,
  borrarPagoEnCurso,
} from '../../api/reservas';

/* =========================================================
   Página de vuelta de Redsys  (/pago/redsys/vuelta)
   ---------------------------------------------------------
   Al terminar en la web de Redsys, el navegador vuelve aquí con
   Ds_SignatureVersion, Ds_MerchantParameters y Ds_Signature en la URL.
   Se reenvían a Reservas (Comp. 2), que comprueba la firma y, si el
   pago está autorizado, confirma la reserva y emite las entradas.
   La URL de vuelta se configura en Reservas: REDSYS_URL_OK / REDSYS_URL_KO.
   ========================================================= */

/**
 * Lee los parámetros de la URL sin convertir "+" en espacio
 * (URLSearchParams lo haría y estropearía el Base64 de Redsys).
 */
function leerParametrosRedsys() {
  const params = {};
  window.location.search.replace(/^\?/, '').split('&').filter(Boolean).forEach((par) => {
    const [k, ...resto] = par.split('=');
    try {
      params[decodeURIComponent(k)] = decodeURIComponent(resto.join('='));
    } catch {
      params[k] = resto.join('=');
    }
  });
  const { Ds_SignatureVersion, Ds_MerchantParameters, Ds_Signature } = params;
  if (!Ds_SignatureVersion || !Ds_MerchantParameters || !Ds_Signature) return null;
  return { Ds_SignatureVersion, Ds_MerchantParameters, Ds_Signature };
}

/** Situación al llegar a la página, antes de llamar a Reservas */
function estadoInicial(pago, parametros) {
  if (!pago?.reservationId) {
    return {
      estado: 'error',
      mensaje: 'No se encuentra la compra en curso en este navegador. Consulta tus entradas en tu perfil.',
    };
  }
  if (!parametros) {
    return {
      estado: 'fallido',
      mensaje: 'El pago no se ha completado (se canceló en Redsys o no se recibió respuesta).',
    };
  }
  return { estado: 'confirmando', mensaje: '' };
}

export default function PagoPage() {
  const navigate = useNavigate();
  const { tokenActivo } = useAuth();
  const [pago] = useState(leerPagoEnCurso);
  const [parametros] = useState(leerParametrosRedsys);
  const [inicial] = useState(() => estadoInicial(pago, parametros));
  const [estado, setEstado] = useState(inicial.estado); // confirmando | ok | fallido | error
  const [mensaje, setMensaje] = useState(inicial.mensaje);
  const [confirmacion, setConfirmacion] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const enviado = useRef(false);

  const token = pago?.token || tokenActivo();

  useEffect(() => {
    // Evita confirmar dos veces (React StrictMode ejecuta los efectos dos veces en desarrollo)
    if (inicial.estado !== 'confirmando' || enviado.current) return;
    enviado.current = true;

    confirmarPagoRedsys(pago.reservationId, parametros, token)
      .then((res) => {
        setConfirmacion(res);
        setEstado('ok');
        borrarPagoEnCurso();
        // La URL deja de llevar los parámetros de Redsys (por si se recarga la página)
        window.history.replaceState(null, '', window.location.pathname);
      })
      .catch((err) => {
        if (err.status === 402) {
          setEstado('fallido');
          setMensaje(`Redsys ha denegado el pago: ${err.message}`);
        } else {
          setEstado('error');
          setMensaje(err.message);
        }
      });
  }, [inicial, pago, parametros, token]);

  async function reintentar() {
    setReintentando(true);
    try {
      const formulario = await iniciarPagoRedsys(pago.reservationId, token);
      guardarPagoEnCurso(pago);
      irARedsys(formulario);
    } catch (err) {
      setReintentando(false);
      setEstado('error');
      setMensaje(err.message);
    }
  }

  async function cancelar() {
    try {
      await cancelarReserva(pago.reservationId, token);
    } catch {
      // si ya caducó, las butacas ya están libres
    }
    borrarPagoEnCurso();
    navigate('/cines');
  }

  if (estado === 'confirmando') {
    return (
      <div className="compra-page">
        <div className="compra-container">
          <div className="compra-panel" style={{ textAlign: 'center', padding: '60px 20px' }}>
            <h2>Confirmando tu pago…</h2>
            <p style={{ color: 'var(--text-muted)' }}>Estamos verificando la respuesta de Redsys y emitiendo tus entradas.</p>
          </div>
        </div>
      </div>
    );
  }

  if (estado === 'ok') {
    return (
      <div className="compra-page">
        <div className="compra-container">
          <div className="compra-panel compra-pago-ok">
            <div className="compra-pago-check">✓</div>
            <h2>Pago completado con éxito</h2>
            <p style={{ fontSize: '1.1rem' }}>
              Reserva nº <strong style={{ color: 'var(--granate)' }}>{confirmacion.reservationId}</strong>
              {confirmacion.paymentReference && <> · Pedido Redsys {confirmacion.paymentReference}</>}
            </p>
            <div className="compra-resumen" style={{ textAlign: 'left', width: '100%', maxWidth: '520px' }}>
              {pago.pelicula && <p><strong>Película:</strong> {pago.pelicula}</p>}
              {pago.cine && <p><strong>Cine:</strong> {pago.cine}</p>}
              {pago.sala && <p><strong>Sala:</strong> {pago.sala}</p>}
              {pago.hora && <p><strong>Sesión:</strong> {pago.hora}</p>}
              {pago.butacas?.length > 0 && <p><strong>Butacas:</strong> {pago.butacas.join(', ')}</p>}
              <p><strong>Importe total:</strong> {Number(confirmacion.totalAmount).toFixed(2)} €</p>
            </div>

            <div className="compra-entradas-qr">
              <p className="compra-redsys-title">Tus entradas</p>
              {(confirmacion.tickets || []).map((codigo, i) => (
                <div key={codigo} className="compra-entrada-codigo">
                  <span>{pago.butacas?.[i] ? `Butaca ${pago.butacas[i]}` : `Entrada ${i + 1}`}</span>
                  <code>{codigo}</code>
                </div>
              ))}
            </div>

            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
              Presenta estos códigos en el control de acceso de la sala.
            </p>
            <div className="compra-nav-btns" style={{ width: '100%', maxWidth: '520px' }}>
              <Link className="home-button" to="/perfil" style={{ flex: 1, justifyContent: 'center' }}>
                Ver mis entradas
              </Link>
              <Link className="home-button home-button-dark" to="/" style={{ flex: 1, justifyContent: 'center' }}>
                Volver al inicio
              </Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="compra-page">
      <div className="compra-container">
        <div className="compra-panel" style={{ textAlign: 'center' }}>
          <h2 className="compra-panel-title">
            {estado === 'fallido' ? 'Pago no completado' : 'No se ha podido confirmar el pago'}
          </h2>
          <div className="cinasee-error" role="alert" style={{ textAlign: 'left' }}>{mensaje}</div>
          {estado === 'fallido' && (
            <p style={{ color: 'var(--text-muted)' }}>
              Tus butacas siguen reservadas unos minutos: puedes volver a intentarlo.
            </p>
          )}
          <div className="compra-nav-btns" style={{ marginTop: '20px' }}>
            {estado === 'fallido' && pago?.reservationId ? (
              <>
                <button className="home-button home-button-dark" type="button" onClick={cancelar}>
                  Cancelar la reserva
                </button>
                <button
                  className="home-button"
                  type="button"
                  onClick={reintentar}
                  disabled={reintentando}
                  style={{ flex: 1, justifyContent: 'center' }}
                >
                  {reintentando ? 'Conectando con Redsys…' : 'Reintentar el pago'}
                </button>
              </>
            ) : (
              <>
                <Link className="home-button home-button-dark" to="/perfil">Ver mis entradas</Link>
                <Link className="home-button" to="/cines" style={{ flex: 1, justifyContent: 'center' }}>
                  Volver a la cartelera
                </Link>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
