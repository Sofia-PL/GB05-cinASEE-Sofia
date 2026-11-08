package com.cinasee.componente2_reservas.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Excepción base de negocio del microservicio. Cada subclase fija el código HTTP
 * que le corresponde según la especificación OpenAPI, y el
 * {@link GlobalExceptionHandler} la transforma en una respuesta JSON uniforme.
 */
@Getter
public abstract class ReservationApiException extends RuntimeException {

    private final HttpStatus status;

    /** Código corto y estable que el frontend puede usar para mostrar mensajes. */
    private final String errorCode;

    protected ReservationApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected ReservationApiException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
