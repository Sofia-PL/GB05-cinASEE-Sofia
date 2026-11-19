package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.clients.dto.CatalogRoom;
import com.cinasee.componente2_reservas.clients.dto.CatalogSession;
import com.cinasee.componente2_reservas.clients.dto.UserProfileDto;
import com.cinasee.componente2_reservas.config.ReservationProperties;
import com.cinasee.componente2_reservas.dto.CreateReservationRequest;
import com.cinasee.componente2_reservas.dto.ReservationResponse;
import com.cinasee.componente2_reservas.dto.SeatMapResponse;
import com.cinasee.componente2_reservas.dto.SeatSelection;
import com.cinasee.componente2_reservas.exceptions.ForbiddenOperationException;
import com.cinasee.componente2_reservas.exceptions.InvalidReservationException;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.exceptions.ResourceNotFoundException;
import com.cinasee.componente2_reservas.exceptions.SeatsUnavailableException;
import com.cinasee.componente2_reservas.exceptions.ServiceUnavailableException;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.models.ReservedSeat;
import com.cinasee.componente2_reservas.models.SeatStatus;
import com.cinasee.componente2_reservas.repositories.ReservationRepository;
import com.cinasee.componente2_reservas.repositories.ReservedSeatRepository;
import com.cinasee.componente2_reservas.security.CallerIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Caso de uso principal: iniciar una reserva (POST /reservations) y consultas.
 *
 * <h2>Flujo de creación</h2>
 * <ol>
 *   <li>Validaciones locales (butacas repetidas, reglas de invitado/cliente/taquilla).</li>
 *   <li><b>Reservas → Cartelera</b> GET /sessions/{showId}: la sesión existe, está
 *       "A_LA_VENTA" y no ha empezado.</li>
 *   <li><b>Reservas → Cartelera</b> GET /rooms/{roomId}: las butacas existen en el mapa.</li>
 *   <li><b>Reservas → Usuarios</b> GET /users/{userId}: perfil y puntos de fidelización.</li>
 *   <li>Cálculo del precio con descuentos.</li>
 *   <li>Bloqueo de butacas en PostgreSQL ({@link SeatLockService#hold}).</li>
 * </ol>
 *
 * Las llamadas HTTP (pasos 2-4) se hacen <b>fuera</b> de la transacción de base
 * de datos a propósito: así no se mantiene abierta una transacción (ni bloqueos)
 * mientras se espera la respuesta de otro microservicio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final RemoteDataService remoteDataService;
    private final PricingService pricingService;
    private final SeatLockService seatLockService;
    private final ReservationRepository reservationRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final ReservationProperties properties;
    private final Clock clock;

    /** Inicia la reserva y bloquea las butacas durante el TTL (10 min). */
    public ReservationResponse createReservation(CreateReservationRequest request, CallerIdentity caller) {
        validateNoDuplicatedSeats(request.seats());
        Long userId = resolveUserId(request, caller);
        String guestEmail = resolveGuestEmail(request, caller, userId);

        // --- Comunicación síncrona con Cartelera (Comp. 1) ---
        CatalogSession session = remoteDataService.getSession(request.showId());
        Instant showStart = ensureSessionOnSale(session);
        CatalogRoom room = remoteDataService.getRoom(session.roomId());
        validateSeatsInsideRoom(request.seats(), room);

        // --- Comunicación síncrona con Usuarios (Comp. 3), solo si hay usuario ---
        UserProfileDto user = userId != null ? remoteDataService.getUser(userId) : null;

        PriceQuote quote = pricingService.quote(session, room, user, showStart, request.seats().size());

        HoldCommand command = new HoldCommand(
                session.id() != null ? session.id() : request.showId(),
                session.movieId(), session.roomId(), showStart,
                userId, guestEmail, caller.subject(),
                request.seats(), quote, properties.currency());

        try {
            return ReservationResponse.from(seatLockService.hold(command));
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            // Carrera ganada por otra petición simultánea: la restricción UNIQUE de
            // (show_id, seat_row, seat_column) ha rechazado nuestro INSERT.
            log.info("Conflicto concurrente al bloquear butacas de la sesión {}: {}", request.showId(), e.getMessage());
            throw new SeatsUnavailableException(List.of());
        }
    }

    /** GET /reservations/{id}: detalle de una reserva propia (o cualquiera si es staff). */
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Long reservationId, CallerIdentity caller) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("La reserva " + reservationId + " no existe"));
        ReservationAccessPolicy.checkCanAccess(reservation, caller);
        return ReservationResponse.from(reservation);
    }

    /**
     * GET /reservations: "mis entradas".
     * - Cliente registrado: las suyas (por userId).
     * - Invitado: las creadas con su token.
     * - Staff/Admin: las de un usuario concreto si pasa userId, o las que ha creado él.
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> listReservations(Long userIdFilter, CallerIdentity caller) {
        List<Reservation> reservations;
        if (caller.isStaff()) {
            reservations = userIdFilter != null
                    ? reservationRepository.findByUserIdOrderByCreatedAtDesc(userIdFilter)
                    : reservationRepository.findByOwnerSubjectOrderByCreatedAtDesc(caller.subject());
        } else if (caller.isRegisteredClient() && caller.userId().isPresent()) {
            Long ownId = caller.userId().get();
            if (userIdFilter != null && !userIdFilter.equals(ownId)) {
                throw new ForbiddenOperationException("Solo puedes consultar tus propias reservas");
            }
            reservations = reservationRepository.findByUserIdOrderByCreatedAtDesc(ownId);
        } else {
            reservations = reservationRepository.findByOwnerSubjectOrderByCreatedAtDesc(caller.subject());
        }
        return reservations.stream().map(ReservationResponse::from).toList();
    }

    /**
     * GET /reservations/shows/{showId}/seats: butacas ocupadas para pintar el mapa.
     * Se excluyen los bloqueos que ya han caducado aunque el barrido aún no los
     * haya borrado, para no mostrar como ocupada una butaca que en realidad está libre.
     */
    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(String showId) {
        Instant now = clock.instant();
        List<SeatMapResponse.OccupiedSeat> occupied = reservedSeatRepository.findByShowIdWithReservation(showId).stream()
                .filter(seat -> isActive(seat, now))
                .sorted(Comparator.comparing(ReservedSeat::getSeatRow).thenComparing(ReservedSeat::getSeatColumn))
                .map(seat -> new SeatMapResponse.OccupiedSeat(seat.getSeatRow(), seat.getSeatColumn(), seat.getStatus()))
                .toList();
        return new SeatMapResponse(showId, occupied);
    }

    // ------------------------------------------------------------------
    // Reglas de negocio privadas
    // ------------------------------------------------------------------

    private boolean isActive(ReservedSeat seat, Instant now) {
        Reservation reservation = seat.getReservation();
        return seat.getStatus() == SeatStatus.SOLD
                || (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT && now.isBefore(reservation.getExpiresAt()))
                // Pago en Redsys en curso: la butaca sigue ocupada aunque hayan pasado los 10 min
                || reservation.isPaymentInProgressAt(now, properties.paymentGrace());
    }

    /** 400 si la misma butaca aparece dos veces en la petición. */
    private void validateNoDuplicatedSeats(List<SeatSelection> seats) {
        Set<String> seen = new HashSet<>();
        for (SeatSelection seat : seats) {
            if (!seen.add(seat.label())) {
                throw new InvalidReservationException("La butaca " + seat.label() + " está repetida en la petición");
            }
        }
    }

    /**
     * Decide a qué usuario del Componente 3 se asocia la reserva:
     * <ul>
     *   <li>Invitado (VISITANTE): no puede indicar userId; compra como invitado.</li>
     *   <li>Cliente registrado: solo para sí mismo. Si no indica userId se usa el de su
     *       token, para que nunca pierda los puntos de fidelización por olvido.</li>
     *   <li>Taquilla/Admin: puede vender a cualquier usuario o como venta anónima.</li>
     * </ul>
     */
    private Long resolveUserId(CreateReservationRequest request, CallerIdentity caller) {
        if (caller.isStaff()) {
            return request.userId();
        }
        if (caller.isRegisteredClient()) {
            Long ownId = caller.userId()
                    .orElseThrow(() -> new ForbiddenOperationException("El token no identifica a un usuario registrado"));
            if (request.userId() != null && !request.userId().equals(ownId)) {
                throw new ForbiddenOperationException("No puedes crear reservas a nombre de otro usuario");
            }
            return ownId;
        }
        if (caller.isGuest()) {
            if (request.userId() != null) {
                throw new ForbiddenOperationException("Un invitado no puede asociar la reserva a un usuario registrado");
            }
            return null;
        }
        throw new ForbiddenOperationException("Rol no autorizado para reservar: " + caller.role());
    }

    /** Las compras de invitado se vinculan únicamente a un correo electrónico (memoria, Comp. 2). */
    private String resolveGuestEmail(CreateReservationRequest request, CallerIdentity caller, Long userId) {
        if (userId != null) {
            return null;
        }
        if (caller.isGuest() && (request.guestEmail() == null || request.guestEmail().isBlank())) {
            throw new InvalidReservationException("guestEmail es obligatorio en las compras de invitado");
        }
        return request.guestEmail();
    }

    /**
     * La sesión debe estar "A_LA_VENTA" y no haber empezado.
     * @return hora de inicio de la sesión
     */
    private Instant ensureSessionOnSale(CatalogSession session) {
        if (session.roomId() == null || session.movieId() == null) {
            throw new ServiceUnavailableException("Cartelera ha devuelto una sesión incompleta (" + session.id() + ")", null);
        }
        if (!CatalogSession.ON_SALE.equals(session.status())) {
            throw new InvalidStateException("SHOW_NOT_ON_SALE",
                    "La sesión " + session.id() + " no está a la venta (estado: " + session.status() + ")");
        }
        Instant start = CatalogTime.toInstant(session.startTime(), properties.catalogTimeZone());
        if (start == null) {
            throw new ServiceUnavailableException("Cartelera ha devuelto una hora de inicio no válida", null);
        }
        if (!start.isAfter(clock.instant())) {
            throw new InvalidStateException("SHOW_ALREADY_STARTED", "La sesión " + session.id() + " ya ha comenzado");
        }
        return start;
    }

    /** 400 si alguna butaca no existe en el mapa de la sala (spec: "butaca no existe en el mapa"). */
    private void validateSeatsInsideRoom(List<SeatSelection> seats, CatalogRoom room) {
        if (room.rowsCount() == null || room.columnsCount() == null) {
            throw new ServiceUnavailableException("Cartelera no ha devuelto las dimensiones de la sala " + room.id(), null);
        }
        List<String> outside = seats.stream()
                .filter(s -> s.row() > room.rowsCount() || s.column() > room.columnsCount())
                .map(SeatSelection::label)
                .toList();
        if (!outside.isEmpty()) {
            throw new InvalidReservationException("Las butacas " + outside + " no existen en la sala " + room.id()
                    + " (" + room.rowsCount() + " filas x " + room.columnsCount() + " columnas)");
        }
    }
}
