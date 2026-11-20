/* =========================================================
   Componente 2 · Reservas, butacas y pagos (Redsys)
   A través del gateway: /v1/reservations/**
   ========================================================= */
import { apiFetch } from './http';

/** GET /v1/reservations/shows/{showId}/seats (público) -> butacas ocupadas (HELD/SOLD) */
export function obtenerButacasOcupadas(showId) {
  return apiFetch(`/v1/reservations/shows/${showId}/seats`);
}

/**
 * POST /v1/reservations -> bloquea las butacas 10 minutos (PENDING_PAYMENT).
 * @param {object} datos { showId, seats: [{row, column}], guestEmail? }
 */
export function crearReserva(datos, token) {
  return apiFetch('/v1/reservations', { method: 'POST', body: datos, token });
}

/** GET /v1/reservations -> reservas del usuario del token */
export function misReservas(token) {
  return apiFetch('/v1/reservations', { token });
}

/** GET /v1/reservations/{id} */
export function obtenerReserva(reservationId, token) {
  return apiFetch(`/v1/reservations/${reservationId}`, { token });
}

/** DELETE /v1/reservations/{id} -> cancela antes de pagar y libera las butacas */
export function cancelarReserva(reservationId, token) {
  return apiFetch(`/v1/reservations/${reservationId}`, { method: 'DELETE', token });
}

/**
 * POST /v1/reservations/{id}/payment/redsys
 * -> { url, fields: { Ds_SignatureVersion, Ds_MerchantParameters, Ds_Signature }, order, amount, paymentDeadline }
 */
export function iniciarPagoRedsys(reservationId, token) {
  return apiFetch(`/v1/reservations/${reservationId}/payment/redsys`, { method: 'POST', token });
}

/**
 * POST /v1/reservations/{id}/payment/redsys/confirm con lo que Redsys devolvió al navegador.
 * Reservas comprueba la firma y, si el pago está autorizado, emite las entradas.
 * -> { reservationId, status, totalAmount, currency, paymentReference, tickets: [...] }
 */
export function confirmarPagoRedsys(reservationId, parametrosRedsys, token) {
  return apiFetch(`/v1/reservations/${reservationId}/payment/redsys/confirm`, {
    method: 'POST',
    body: parametrosRedsys,
    token,
  });
}

/** POST /v1/reservations/tickets/{code}/check-in (taquilla) -> valida la entrada en puerta */
export function validarEntrada(ticketCode, token) {
  return apiFetch(`/v1/reservations/tickets/${encodeURIComponent(ticketCode)}/check-in`, {
    method: 'POST',
    token,
  });
}

/**
 * Envía el navegador a la web de pago de Redsys.
 * Redsys solo admite un formulario POST normal (no fetch), así que se crea
 * un <form> oculto con los campos firmados por Reservas y se envía.
 */
export function irARedsys({ url, fields }) {
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = url;
  Object.entries(fields).forEach(([name, value]) => {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = name;
    input.value = value;
    form.appendChild(input);
  });
  document.body.appendChild(form);
  form.submit();
}

/* ---------------------------------------------------------
   Pago en curso: se guarda antes de ir a Redsys para que la
   página de vuelta sepa qué reserva confirmar (la web de
   Redsys es otra página, así que el estado de React se pierde).
   --------------------------------------------------------- */
const PAGO_KEY = 'cinasee_pago_redsys';

export function guardarPagoEnCurso(info) {
  try {
    sessionStorage.setItem(PAGO_KEY, JSON.stringify(info));
  } catch {
    // sin almacenamiento: la página de vuelta lo indicará
  }
}

export function leerPagoEnCurso() {
  try {
    return JSON.parse(sessionStorage.getItem(PAGO_KEY) || 'null');
  } catch {
    return null;
  }
}

/** Sesión elegida para comprar (la guarda la página de compra por si se recarga o se va al login) */
export const COMPRA_KEY = 'cinasee_compra';

/** Tras pagar, se olvida la compra para que no se pueda retomar volviendo atrás */
export function borrarCompraGuardada() {
  try {
    sessionStorage.removeItem(COMPRA_KEY);
  } catch {
    // ignorar
  }
}

export function borrarPagoEnCurso() {
  try {
    sessionStorage.removeItem(PAGO_KEY);
  } catch {
    // ignorar
  }
}

/** Texto de una butaca a partir de fila y columna (1-based): fila 1 -> "A" */
export function nombreButaca(row, column) {
  return `${String.fromCharCode(64 + row)}${column}`;
}

/** "QR-8901-8-12-K7M2QX9P" -> "H12" (el código de la entrada lleva la fila y la columna) */
export function butacaDeCodigo(codigo) {
  const m = /^QR-\d+-(\d+)-(\d+)-/.exec(String(codigo || ''));
  return m ? nombreButaca(Number(m[1]), Number(m[2])) : '';
}

/** Descuentos que aplica Reservas */
export const DESCUENTOS = {
  NONE: '',
  SPECTATOR_DAY: 'Día del espectador (−20 %)',
  LOYALTY: 'Descuento de fidelización (−10 %)',
};
