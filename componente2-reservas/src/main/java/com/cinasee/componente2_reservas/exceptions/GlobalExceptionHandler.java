package com.cinasee.componente2_reservas.exceptions;

import com.cinasee.componente2_reservas.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

/**
 * Manejador global de excepciones: traduce cualquier error a una respuesta JSON
 * homogénea ({@link ApiError}) con el código HTTP que fija la especificación
 * OpenAPI del Componente 2.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Errores de negocio propios: cada excepción ya sabe qué código HTTP le toca. */
    @ExceptionHandler(ReservationApiException.class)
    public ResponseEntity<ApiError> handleApiException(ReservationApiException ex, HttpServletRequest request) {
        List<String> details = ex instanceof SeatsUnavailableException seats
                ? seats.getConflictingSeats()
                : List.of();
        if (ex.getStatus().is5xxServerError()) {
            log.warn("{} {} -> {}: {}", request.getMethod(), request.getRequestURI(), ex.getStatus(), ex.getMessage());
        }
        return build(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), details, request);
    }

    /** 400: fallos de @Valid en el cuerpo (p. ej. fila 0, más de 10 butacas, showId nulo). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La petición contiene datos no válidos", details, request);
    }

    /** 400: validación de parámetros de método (@RequestParam/@PathVariable con restricciones). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<String> details = ex.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Parámetros no válidos", details, request);
    }

    /** 400: JSON mal formado o valores de enum desconocidos (p. ej. gateway distinto de REDSYS). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "El cuerpo de la petición no es un JSON válido", List.of(), request);
    }

    /** 400: parámetros de URL con tipo incorrecto (p. ej. /reservations/abc). */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadParameter(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", ex.getMessage(), List.of(), request);
    }

    /**
     * 409: conflictos detectados por la base de datos. Llegan aquí si la violación
     * del UNIQUE de butacas se produce en el COMMIT (fuera del servicio) o si dos
     * transacciones compiten por la misma fila.
     */
    @ExceptionHandler({DataIntegrityViolationException.class,
            OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleConcurrency(Exception ex, HttpServletRequest request) {
        log.info("Conflicto de concurrencia en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "El recurso ha sido modificado por otra operación simultánea. Inténtalo de nuevo.", List.of(), request);
    }

    /** 500: cualquier error no previsto. No se filtran detalles internos al cliente. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno del servidor", List.of(), request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           List<String> details, HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), status.value(), code, message, details, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
