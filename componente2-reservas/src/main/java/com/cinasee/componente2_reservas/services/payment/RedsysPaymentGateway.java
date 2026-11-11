package com.cinasee.componente2_reservas.services.payment;

import com.cinasee.componente2_reservas.clients.dto.GatewayChargeRequest;
import com.cinasee.componente2_reservas.clients.dto.GatewayChargeResponse;
import com.cinasee.componente2_reservas.clients.dto.GatewayRefundRequest;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link PaymentGateway} cuando la pasarela es Redsys ({@code app.payment.mode=redsys}).
 *
 * <ul>
 *   <li><b>Cobro:</b> con Redsys no hay cobro directo servidor a servidor; el cliente paga
 *       en la web de Redsys (redirección). El flujo lo gestiona
 *       {@code RedsysPaymentService}, así que el cobro síncrono responde 409 indicando
 *       el endpoint correcto.</li>
 *   <li><b>Devolución:</b> por ahora se registra como devolución MANUAL (decisión D6): la
 *       reserva pasa a REFUNDED y libera sus butacas, y el importe se devuelve desde el
 *       panel de administración de Redsys. Automatizarla (operación tipo 3 por el servicio
 *       web de Redsys) queda como mejora pendiente.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.mode", havingValue = "redsys")
public class RedsysPaymentGateway implements PaymentGateway {

    @Override
    public GatewayChargeResponse charge(GatewayChargeRequest request) {
        throw new InvalidStateException("USE_REDSYS_FLOW",
                "Con Redsys el pago se hace en su web: usa POST /reservations/{id}/payment/redsys");
    }

    @Override
    public GatewayChargeResponse refund(GatewayRefundRequest request) {
        log.warn("Devolución de {} {} del pedido Redsys {}: registrada como MANUAL; hay que hacerla en el panel de Redsys",
                request.amount(), request.currency(), request.transactionId());
        return new GatewayChargeResponse("APPROVED", "MANUAL-REFUND-" + request.transactionId(), null);
    }
}
