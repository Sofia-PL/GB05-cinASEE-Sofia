package com.cinasee.componente2_reservas.services.payment;

import com.cinasee.componente2_reservas.clients.RedsysClient;
import com.cinasee.componente2_reservas.clients.dto.GatewayChargeRequest;
import com.cinasee.componente2_reservas.clients.dto.GatewayChargeResponse;
import com.cinasee.componente2_reservas.clients.dto.GatewayRefundRequest;
import com.cinasee.componente2_reservas.exceptions.ServiceUnavailableException;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Adaptador hacia la pasarela Redsýs a través de {@link RedsysClient} (OpenFeign).
 *
 * Traducción de errores:
 * <ul>
 *   <li>HTTP 402 o 4xx de la pasarela → pago rechazado (DECLINED, el cliente recibe 402)</li>
 *   <li>timeout, conexión rechazada o 5xx → {@link ServiceUnavailableException} (503)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.mode", havingValue = "redsys")
public class RedsysPaymentGateway implements PaymentGateway {

    private final RedsysClient redsysClient;

    @Override
    public GatewayChargeResponse charge(GatewayChargeRequest request) {
        return call(() -> redsysClient.charge(request));
    }

    @Override
    public GatewayChargeResponse refund(GatewayRefundRequest request) {
        return call(() -> redsysClient.refund(request));
    }

    private GatewayChargeResponse call(Supplier<GatewayChargeResponse> operation) {
        try {
            GatewayChargeResponse response = operation.get();
            if (response == null) {
                throw new ServiceUnavailableException("La pasarela de pagos ha devuelto una respuesta vacía", null);
            }
            return response;
        } catch (RetryableException e) {
            log.warn("Pasarela de pagos no disponible: {}", e.getMessage());
            throw new ServiceUnavailableException("La pasarela de pagos (Redsýs) no está disponible", e);
        } catch (FeignException e) {
            if (e.status() >= 400 && e.status() < 500) {
                // La pasarela ha procesado la petición y la rechaza (tarjeta, fondos...)
                return new GatewayChargeResponse("DECLINED", null, "Rechazado por la pasarela (HTTP " + e.status() + ")");
            }
            log.warn("Error de la pasarela de pagos (HTTP {}): {}", e.status(), e.getMessage());
            throw new ServiceUnavailableException("La pasarela de pagos (Redsýs) no ha podido procesar el pago", e);
        }
    }
}
