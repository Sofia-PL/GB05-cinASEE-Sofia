package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.config.ReservationProperties;
import com.cinasee.componente2_reservas.dto.BoxOfficeDataResponse;
import com.cinasee.componente2_reservas.dto.ShowStatsResponse;
import com.cinasee.componente2_reservas.exceptions.InvalidReservationException;
import com.cinasee.componente2_reservas.repositories.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Datos agregados de ventas que consume el Componente 4 (Analítica) de forma síncrona.
 *
 * <ul>
 *   <li>Analytics → Reservas → GET /reservations/stats (ocupación por sesión/sala)</li>
 *   <li>Analytics → Reservas → GET /reservations/box-office-data (taquilla por película)</li>
 * </ul>
 *
 * Reservas solo expone sus propios datos (butacas vendidas e importes). El cruce con
 * aforos, distribuidoras o títulos lo hace Analítica consultando a Cartelera: ningún
 * servicio accede a la base de datos de otro.
 *
 * Las fechas se interpretan en UTC y el fin de rango es inclusivo (día completo).
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** Rango máximo de un informe Box Office (la spec de Analítica prevé un límite). */
    static final long MAX_BOX_OFFICE_DAYS = 366;

    private static final Instant MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("9999-12-31T00:00:00Z");

    private final ReservationRepository reservationRepository;
    private final ReservationProperties properties;

    /**
     * @param showId filtra por una sesión (opcional)
     * @param roomId filtra por una sala (opcional)
     * @param from   fecha mínima de la sesión (opcional, inclusiva)
     * @param to     fecha máxima de la sesión (opcional, inclusiva)
     */
    @Transactional(readOnly = true)
    public List<ShowStatsResponse> getShowStats(String showId, String roomId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        Instant fromInstant = from != null ? startOfDay(from) : MIN_INSTANT;
        Instant toInstant = to != null ? startOfDay(to.plusDays(1)) : MAX_INSTANT;

        return reservationRepository.aggregateSalesByShow(fromInstant, toInstant).stream()
                // Filtros opcionales en memoria sobre el resultado ya agregado (una fila por sesión)
                .filter(row -> showId == null || showId.equals(row.showId()))
                .filter(row -> roomId == null || roomId.equals(row.roomId()))
                .map(row -> new ShowStatsResponse(row.showId(), row.movieId(), row.roomId(), row.showStartTime(),
                        nullToZero(row.ticketsSold()), nullToZero(row.reservations()),
                        row.revenue() != null ? row.revenue() : BigDecimal.ZERO, properties.currency()))
                .toList();
    }

    /**
     * @param startDate fecha inicial del periodo de pago (obligatoria, inclusiva)
     * @param endDate   fecha final del periodo de pago (obligatoria, inclusiva)
     * @param movieId   filtra por película (opcional)
     */
    @Transactional(readOnly = true)
    public BoxOfficeDataResponse getBoxOfficeData(LocalDate startDate, LocalDate endDate, String movieId) {
        validateRange(startDate, endDate);
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_BOX_OFFICE_DAYS) {
            throw new InvalidReservationException("El rango del informe no puede superar " + MAX_BOX_OFFICE_DAYS + " días");
        }
        List<BoxOfficeDataResponse.MovieBoxOffice> movies = reservationRepository
                .aggregateSalesByMovie(startOfDay(startDate), startOfDay(endDate.plusDays(1))).stream()
                .filter(row -> movieId == null || movieId.equals(row.movieId()))
                .map(row -> new BoxOfficeDataResponse.MovieBoxOffice(row.movieId(),
                        nullToZero(row.ticketsSold()), nullToZero(row.reservations()),
                        row.grossRevenue() != null ? row.grossRevenue() : BigDecimal.ZERO))
                .toList();
        return new BoxOfficeDataResponse(startDate, endDate, properties.currency(), movies);
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidReservationException("La fecha inicial no puede ser posterior a la final");
        }
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static long nullToZero(Long value) {
        return value != null ? value : 0L;
    }
}
