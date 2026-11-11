package com.cinasee.componente2_reservas.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Respuesta de POST /reservations/{id}/payment/redsys: lo que necesita el frontend
 * para enviar al cliente a la web de pago de Redsys.
 *
 * El frontend debe crear un formulario HTML con {@code method="POST"} y
 * {@code action=url}, un campo oculto por cada entrada de {@code fields}, y enviarlo.
 * Es el navegador del cliente quien va a Redsys: la tarjeta nunca pasa por CinAsee.
 *
 * @param url            URL del TPV Virtual de Redsys (entorno de pruebas o real)
 * @param fields         campos del formulario: Ds_SignatureVersion, Ds_MerchantParameters, Ds_Signature
 * @param order          número de pedido enviado a Redsys (Ds_Merchant_Order)
 * @param paymentDeadline hasta cuándo se mantienen bloqueadas las butacas mientras se paga
 */
public record RedsysPaymentForm(
        Long reservationId,
        String url,
        Map<String, String> fields,
        String order,
        BigDecimal amount,
        String currency,
        Instant paymentDeadline
) {
}
