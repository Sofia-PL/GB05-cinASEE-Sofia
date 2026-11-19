package com.cinasee.componente2_reservas.controllers;

import com.cinasee.componente2_reservas.dto.BoxOfficeDataResponse;
import com.cinasee.componente2_reservas.dto.ShowStatsResponse;
import com.cinasee.componente2_reservas.services.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints de datos agregados que consume el Componente 4 (Analítica) de forma
 * síncrona. Protegidos para ADMIN y STAFF_TAQUILLA en {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** Analytics -> Reservas -> GET /reservations/stats (cálculo de ocupación). */
    @GetMapping("/stats")
    public List<ShowStatsResponse> getReservationStats(
            @RequestParam(required = false) String showId,
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.getShowStats(showId, roomId, from, to);
    }

    /** Analytics -> Reservas -> GET /reservations/box-office-data (informe de taquilla). */
    @GetMapping("/box-office-data")
    public BoxOfficeDataResponse getBoxOfficeData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String movieId) {
        return reportService.getBoxOfficeData(startDate, endDate, movieId);
    }
}
