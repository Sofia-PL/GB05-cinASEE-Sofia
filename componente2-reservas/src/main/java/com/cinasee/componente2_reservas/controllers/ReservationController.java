package com.cinasee.componente2_reservas.controllers;

import com.cinasee.componente2_reservas.dto.CreateReservationRequest;
import com.cinasee.componente2_reservas.dto.PaymentRequest;
import com.cinasee.componente2_reservas.dto.RedsysPaymentForm;
import com.cinasee.componente2_reservas.dto.RedsysResultRequest;
import com.cinasee.componente2_reservas.dto.ReservationResponse;
import com.cinasee.componente2_reservas.dto.SeatMapResponse;
import com.cinasee.componente2_reservas.dto.TicketConfirmation;
import com.cinasee.componente2_reservas.security.CallerIdentity;
import com.cinasee.componente2_reservas.services.CancellationService;
import com.cinasee.componente2_reservas.services.PaymentService;
import com.cinasee.componente2_reservas.services.RedsysPaymentService;
import com.cinasee.componente2_reservas.services.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Adaptador de entrada REST del ciclo de compra (operaciones del OpenAPI componente_2.yaml).
 *
 * Los controladores son finos: extraen la identidad del JWT, delegan en los
 * servicios y construyen la respuesta HTTP. Toda la lógica está en services/.
 *
 * Ruta base: {@code /v1} (context-path) + {@code /reservations}.
 */
@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final CancellationService cancellationService;
    private final RedsysPaymentService redsysPaymentService;

    /** createReservation: inicia la reserva y bloquea butacas 10 min → 201 Created. */
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ReservationResponse created = reservationService.createReservation(request, CallerIdentity.from(jwt));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** listReservations: "mis entradas" (o las de un usuario, si quien consulta es staff). */
    @GetMapping
    public List<ReservationResponse> listReservations(
            @RequestParam(required = false) @Positive Long userId,
            @AuthenticationPrincipal Jwt jwt) {
        return reservationService.listReservations(userId, CallerIdentity.from(jwt));
    }

    /** getReservation: detalle de una reserva (estado, TTL restante, entradas). */
    @GetMapping("/{reservationId}")
    public ReservationResponse getReservation(@PathVariable Long reservationId,
                                              @AuthenticationPrincipal Jwt jwt) {
        return reservationService.getReservation(reservationId, CallerIdentity.from(jwt));
    }

    /** payReservation: cobra, confirma, emite entradas y publica PaymentCompleted → 200. */
    @PostMapping("/{reservationId}/payment")
    public TicketConfirmation payReservation(@PathVariable Long reservationId,
                                             @Valid @RequestBody PaymentRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        return paymentService.payReservation(reservationId, request, CallerIdentity.from(jwt));
    }

    /**
     * startRedsysPayment: devuelve el formulario firmado para enviar al cliente a la
     * web de pago de Redsys. La reserva sigue pendiente hasta la confirmación.
     */
    @PostMapping("/{reservationId}/payment/redsys")
    public RedsysPaymentForm startRedsysPayment(@PathVariable Long reservationId,
                                                @AuthenticationPrincipal Jwt jwt) {
        return redsysPaymentService.startPayment(reservationId, CallerIdentity.from(jwt));
    }

    /**
     * confirmRedsysPayment: el frontend reenvía los parámetros firmados con los que Redsys
     * devolvió al cliente. Si la firma es válida y el pago se autorizó → entradas (200).
     */
    @PostMapping("/{reservationId}/payment/redsys/confirm")
    public TicketConfirmation confirmRedsysPayment(@PathVariable Long reservationId,
                                                   @Valid @RequestBody RedsysResultRequest result,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return redsysPaymentService.confirmPayment(reservationId, result, CallerIdentity.from(jwt));
    }

    /** cancelReservation: anula una reserva pendiente o devuelve una pagada → 200. */
    @DeleteMapping("/{reservationId}")
    public ReservationResponse cancelReservation(@PathVariable Long reservationId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return cancellationService.cancelReservation(reservationId, CallerIdentity.from(jwt));
    }

    /** getSeatMap: butacas ocupadas de una sesión (público, sin token). */
    @GetMapping("/shows/{showId}/seats")
    public SeatMapResponse getSeatMap(@PathVariable String showId) {
        return reservationService.getSeatMap(showId);
    }
}
