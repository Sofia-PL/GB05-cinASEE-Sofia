package com.cinasee.componente2_reservas.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 409: conflicto de concurrencia. Alguna de las butacas pedidas ya está bloqueada
 * por otra reserva o vendida. Incluye la lista de butacas en conflicto (formato
 * "fila-columna") para que el frontend pueda marcarlas en el mapa.
 */
@Getter
public class SeatsUnavailableException extends ReservationApiException {

    private final List<String> conflictingSeats;

    public SeatsUnavailableException(List<String> conflictingSeats) {
        super(HttpStatus.CONFLICT, "SEATS_UNAVAILABLE",
                conflictingSeats.isEmpty()
                        ? "Una o varias butacas acaban de ser bloqueadas por otro usuario."
                        : "Butacas no disponibles: " + String.join(", ", conflictingSeats));
        this.conflictingSeats = List.copyOf(conflictingSeats);
    }
}
