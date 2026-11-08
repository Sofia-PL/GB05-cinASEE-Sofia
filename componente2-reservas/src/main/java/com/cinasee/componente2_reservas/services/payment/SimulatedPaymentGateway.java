package com.cinasee.componente2_reservas.services.payment;

import com.cinasee.componente2_reservas.clients.dto.GatewayChargeRequest;
import com.cinasee.componente2_reservas.clients.dto.GatewayChargeResponse;
import com.cinasee.componente2_reservas.clients.dto.GatewayRefundRequest;
import com.cinasee.componente2_reservas.exceptions.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pasarela simulada para desarrollo, pruebas y demostraciones.
 *
 * El resultado depende del prefijo del paymentToken, para poder probar todos
 * los caminos de la especificación sin una pasarela real:
 * <ul>
 *   <li>{@code tok_declined...} → pago rechazado (HTTP 402)</li>
 *   <li>{@code tok_unavailable...} → pasarela caída (HTTP 503)</li>
 *   <li>cualquier otro → pago aprobado (HTTP 200)</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    public static final String DECLINED_PREFIX = "tok_declined";
    public static final String UNAVAILABLE_PREFIX = "tok_unavailable";

    @Override
    public GatewayChargeResponse charge(GatewayChargeRequest request) {
        String token = request.paymentToken();
        if (token.startsWith(UNAVAILABLE_PREFIX)) {
            throw new ServiceUnavailableException("La pasarela de pagos (simulada) no está disponible", null);
        }
        if (token.startsWith(DECLINED_PREFIX)) {
            log.info("[PASARELA SIMULADA] Pedido {} rechazado", request.orderId());
            return new GatewayChargeResponse("DECLINED", null, "Fondos insuficientes (simulado)");
        }
        String transactionId = "SIM-" + UUID.randomUUID();
        log.info("[PASARELA SIMULADA] Pedido {} cobrado: {} {} (tx {})",
                request.orderId(), request.amount(), request.currency(), transactionId);
        return new GatewayChargeResponse("APPROVED", transactionId, null);
    }

    @Override
    public GatewayChargeResponse refund(GatewayRefundRequest request) {
        String refundId = "SIM-REF-" + UUID.randomUUID();
        log.info("[PASARELA SIMULADA] Devuelto {} {} de la tx {} (ref {})",
                request.amount(), request.currency(), request.transactionId(), refundId);
        return new GatewayChargeResponse("APPROVED", refundId, null);
    }
}
