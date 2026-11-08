package com.cinasee.componente2_reservas.clients;

import com.cinasee.componente2_reservas.clients.dto.GatewayChargeRequest;
import com.cinasee.componente2_reservas.clients.dto.GatewayChargeResponse;
import com.cinasee.componente2_reservas.clients.dto.GatewayRefundRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cliente OpenFeign hacia la pasarela de pago externa (Redsýs).
 *
 * COMUNICACIÓN SÍNCRONA de tipo COMANDO: Reservas -> PasarelaExterna -> POST /charge.
 *
 * Solo se utiliza cuando {@code app.payment.mode=redsys}. En desarrollo se usa
 * la pasarela simulada ({@code SimulatedPaymentGateway}) porque no disponemos de
 * credenciales reales de Redsýs. La URL apunta a un adaptador REST de la
 * pasarela (o a un mock WireMock en pruebas).
 *
 * No reutiliza {@link ForwardJwtFeignConfig}: nunca se envía el JWT del usuario
 * a un tercero.
 */
@FeignClient(name = "redsys", url = "${app.payment.redsys.url}")
public interface RedsysClient {

    @PostMapping("/charge")
    GatewayChargeResponse charge(@RequestBody GatewayChargeRequest request);

    @PostMapping("/refund")
    GatewayChargeResponse refund(@RequestBody GatewayRefundRequest request);
}
