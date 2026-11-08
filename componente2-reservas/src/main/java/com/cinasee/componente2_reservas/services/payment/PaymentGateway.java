package com.cinasee.componente2_reservas.services.payment;

import com.cinasee.componente2_reservas.clients.dto.GatewayChargeRequest;
import com.cinasee.componente2_reservas.clients.dto.GatewayChargeResponse;
import com.cinasee.componente2_reservas.clients.dto.GatewayRefundRequest;
import com.cinasee.componente2_reservas.exceptions.ServiceUnavailableException;

/**
 * Puerto de salida hacia la pasarela de pago externa (arquitectura hexagonal).
 *
 * La lógica de negocio solo conoce esta interfaz. Hay dos adaptadores y se
 * elige uno con la propiedad {@code app.payment.mode}:
 * <ul>
 *   <li>{@code simulated} (por defecto): {@link SimulatedPaymentGateway}, para desarrollo y tests.</li>
 *   <li>{@code redsys}: {@link RedsysPaymentGateway}, llamada HTTP real vía OpenFeign.</li>
 * </ul>
 */
public interface PaymentGateway {

    /**
     * Cobra el importe indicado.
     *
     * @return respuesta con status APPROVED o DECLINED
     * @throws ServiceUnavailableException si la pasarela no responde (→ 503)
     */
    GatewayChargeResponse charge(GatewayChargeRequest request);

    /**
     * Devuelve un cobro previo.
     *
     * @throws ServiceUnavailableException si la pasarela no responde (→ 503)
     */
    GatewayChargeResponse refund(GatewayRefundRequest request);
}
