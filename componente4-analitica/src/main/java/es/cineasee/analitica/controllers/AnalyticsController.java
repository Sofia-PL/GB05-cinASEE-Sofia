package es.cineasee.analitica.controllers;

import es.cineasee.analitica.dto.BoxOfficeReportDTO;
import es.cineasee.analitica.dto.OccupancyMetricDTO;
import es.cineasee.analitica.dto.RecommendationResponseDTO;
import es.cineasee.analitica.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/metrics/occupancy")
    public ResponseEntity<OccupancyMetricDTO> getOccupancyRates(
            @RequestParam("cinemaId") String cinemaId,
            @RequestParam(value = "screenId", required = false) String screenId,
            @RequestParam(value = "dayOfWeek", required = false) Integer dayOfWeek,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime) {

        OccupancyMetricDTO metric = analyticsService.calculateOccupancy(
                cinemaId, screenId, dayOfWeek, startTime, endTime);
        return ResponseEntity.ok(metric);
    }

    @GetMapping("/reports/box-office")
    public ResponseEntity<BoxOfficeReportDTO> getBoxOfficeReport(
            @RequestParam(value = "distributorId", required = false) Long distributorId,
            @RequestParam(value = "movieId", required = false) String movieId,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        BoxOfficeReportDTO report = analyticsService.generateBoxOfficeReport(
                distributorId, movieId, startDate, endDate);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/recommendations/users/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_TAQUILLA') or #userId.toString() == authentication.name")
    public ResponseEntity<RecommendationResponseDTO> getUserRecommendations(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "limit", defaultValue = "5") Integer limit) {

        RecommendationResponseDTO recommendations = analyticsService.getRecommendationsForUser(userId, limit);
        return ResponseEntity.ok(recommendations);
    }
}

