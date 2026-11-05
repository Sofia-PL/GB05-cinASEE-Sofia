package es.cineasee.analitica.services;

import es.cineasee.analitica.clients.CarteleraClient;
import es.cineasee.analitica.clients.UsuariosClient;
import es.cineasee.analitica.clients.dto.MovieDTO;
import es.cineasee.analitica.clients.dto.PurchaseHistoryItemDTO;
import es.cineasee.analitica.clients.dto.RoomDTO;
import es.cineasee.analitica.clients.dto.UserProfileDTO;
import es.cineasee.analitica.dto.BoxOfficeReportDTO;
import es.cineasee.analitica.dto.OccupancyMetricDTO;
import es.cineasee.analitica.dto.RecommendationResponseDTO;
import es.cineasee.analitica.dto.RecommendationResponseDTO.RecommendationItemDTO;
import es.cineasee.analitica.repositories.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final CarteleraClient carteleraClient;
    private final UsuariosClient usuariosClient;

    public OccupancyMetricDTO calculateOccupancy(Long cinemaId, Long screenId, Integer dayOfWeek, String startTime, String endTime) {
        int totalCapacity = 0;

        try {
            if (screenId != null) {
                RoomDTO room = carteleraClient.getRoomById(screenId);
                if (room != null && room.getCapacity() != null) {
                    totalCapacity = room.getCapacity();
                } else {
                    totalCapacity = 150; // Aforo por defecto
                }
            } else {
                List<RoomDTO> rooms = carteleraClient.getAllRooms();
                if (rooms != null && !rooms.isEmpty()) {
                    totalCapacity = rooms.stream()
                            .mapToInt(r -> r.getCapacity() != null ? r.getCapacity() : 100)
                            .sum();
                } else {
                    totalCapacity = 500;
                }
            }
        } catch (Exception ex) {
            log.warn("No se pudo obtener aforo desde CarteleraClient: {}. Usando aforo estimado.", ex.getMessage());
            totalCapacity = (screenId != null) ? 150 : 500;
        }

        Long ticketsSoldLong = analyticsEventRepository.sumTicketsSold(cinemaId, screenId, dayOfWeek);
        int totalTicketsSold = ticketsSoldLong != null ? ticketsSoldLong.intValue() : 0;

        double occupancyPercentage = 0.0;
        if (totalCapacity > 0) {
            occupancyPercentage = Math.min(100.0, Math.round(((double) totalTicketsSold / totalCapacity) * 10000.0) / 100.0);
        }

        String timeRange = null;
        if (startTime != null && endTime != null) {
            timeRange = startTime + "-" + endTime;
        }

        return OccupancyMetricDTO.builder()
                .cinemaId(cinemaId)
                .screenId(screenId)
                .dayOfWeek(dayOfWeek)
                .timeRange(timeRange)
                .totalCapacity(totalCapacity)
                .totalTicketsSold(totalTicketsSold)
                .occupancyPercentage(occupancyPercentage)
                .build();
    }

    public BoxOfficeReportDTO generateBoxOfficeReport(Long distributorId, Long movieId, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de inicio (startDate) no puede ser posterior a la fecha de fin (endDate)");
        }

        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        List<Object[]> rows = analyticsEventRepository.findBoxOfficeAggregate(
                start.atStartOfDay(),
                end.atTime(LocalTime.MAX),
                movieId,
                distributorId
        );

        long totalTicketsSold = 0L;
        double grossRevenue = 0.0;
        String resolvedTitle = null;

        if (rows != null && !rows.isEmpty()) {
            for (Object[] row : rows) {
                Long tickets = row[3] != null ? ((Number) row[3]).longValue() : 0L;
                Double revenue = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
                totalTicketsSold += tickets;
                grossRevenue += revenue;
                if (resolvedTitle == null && row[1] != null) {
                    resolvedTitle = row[1].toString();
                }
            }
        }

        if (resolvedTitle == null && movieId != null) {
            try {
                MovieDTO movie = carteleraClient.getMovieById(movieId);
                if (movie != null) {
                    resolvedTitle = movie.getTitle();
                }
            } catch (Exception ex) {
                log.warn("No se pudo obtener título para la película ID {}: {}", movieId, ex.getMessage());
                resolvedTitle = "Película #" + movieId;
            }
        }

        if (resolvedTitle == null) {
            resolvedTitle = "Resumen General Box Office";
        }

        return BoxOfficeReportDTO.builder()
                .distributorId(distributorId)
                .movieId(movieId)
                .movieTitle(resolvedTitle)
                .period(BoxOfficeReportDTO.PeriodDTO.builder()
                        .startDate(start)
                        .endDate(end)
                        .build())
                .totalTicketsSold(totalTicketsSold)
                .grossRevenue(Math.round(grossRevenue * 100.0) / 100.0)
                .currency("EUR")
                .build();
    }

    public RecommendationResponseDTO getRecommendationsForUser(Long userId, Integer limit) {
        UserProfileDTO userProfile;
        try {
            userProfile = usuariosClient.getUserProfile(userId);
        } catch (Exception ex) {
            log.error("Error al consultar perfil del usuario {}: {}", userId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado con ID: " + userId);
        }

        List<PurchaseHistoryItemDTO> history = Collections.emptyList();
        try {
            List<PurchaseHistoryItemDTO> fetchedHistory = usuariosClient.getUserHistory(userId);
            if (fetchedHistory != null) {
                history = fetchedHistory;
            }
        } catch (Exception ex) {
            log.warn("No se pudo obtener el historial para el usuario {}: {}", userId, ex.getMessage());
        }

        List<MovieDTO> allMovies;
        try {
            allMovies = carteleraClient.getMovies(null, true);
            if (allMovies == null || allMovies.isEmpty()) {
                allMovies = carteleraClient.getMovies(null, false);
            }
        } catch (Exception ex) {
            log.error("No se pudo obtener el catálogo de películas de CarteleraClient: {}", ex.getMessage());
            allMovies = Collections.emptyList();
        }

        Set<Long> watchedMovieIds = history.stream()
                .map(PurchaseHistoryItemDTO::getMovieId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> preferredGenres = new HashSet<>();
        if (userProfile.getPreferredGenres() != null) {
            preferredGenres.addAll(userProfile.getPreferredGenres().stream()
                    .filter(Objects::nonNull)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet()));
        }

        // Géneros extraídos del historial y de los eventos registrados
        List<Object[]> topGenresFromDb = analyticsEventRepository.findTopGenresByUserId(userId);
        Set<String> historicalGenres = new HashSet<>();
        if (topGenresFromDb != null) {
            for (Object[] gRow : topGenresFromDb) {
                if (gRow[0] != null) {
                    historicalGenres.add(gRow[0].toString().toUpperCase());
                }
            }
        }

        List<RecommendationItemDTO> recommendationItems = new ArrayList<>();

        for (MovieDTO movie : allMovies) {
            if (movie.getId() == null) continue;

            double score = 0.50;
            String reason = "Película destacada en cartelera";
            String genre = movie.getGenre() != null ? movie.getGenre().toUpperCase() : "";

            boolean isPreferred = preferredGenres.contains(genre);
            boolean isHistorical = historicalGenres.contains(genre);
            boolean alreadyWatched = watchedMovieIds.contains(movie.getId());

            if (isPreferred) {
                score += 0.45;
                reason = "Basado en tu preferencia por " + movie.getGenre();
            } else if (isHistorical) {
                score += 0.35;
                reason = "Similar a películas que has visto recientemente";
            }

            if (alreadyWatched) {
                score -= 0.60;
            }

            score = Math.max(0.1, Math.min(0.99, Math.round(score * 100.0) / 100.0));

            recommendationItems.add(RecommendationItemDTO.builder()
                    .movieId(movie.getId())
                    .title(movie.getTitle())
                    .matchScore(score)
                    .reason(reason)
                    .build());
        }

        // Ordenar por afinidad descendente
        recommendationItems.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));

        int maxLimit = (limit != null && limit > 0) ? limit : 5;
        List<RecommendationItemDTO> limitedList = recommendationItems.stream()
                .limit(maxLimit)
                .collect(Collectors.toList());

        return RecommendationResponseDTO.builder()
                .userId(userId)
                .recommendations(limitedList)
                .build();
    }
}

