package es.cineasee.analitica.services;

import es.cineasee.analitica.clients.CarteleraClient;
import es.cineasee.analitica.clients.ReservasClient;
import es.cineasee.analitica.clients.UsuariosClient;
import es.cineasee.analitica.clients.dto.BoxOfficeDataDTO;
import es.cineasee.analitica.clients.dto.MovieDTO;
import es.cineasee.analitica.clients.dto.PurchaseHistoryItemDTO;
import es.cineasee.analitica.clients.dto.RoomDTO;
import es.cineasee.analitica.clients.dto.ShowStatsDTO;
import es.cineasee.analitica.clients.dto.UserProfileDTO;
import es.cineasee.analitica.dto.BoxOfficeReportDTO;
import es.cineasee.analitica.dto.OccupancyMetricDTO;
import es.cineasee.analitica.dto.RecommendationResponseDTO;
import es.cineasee.analitica.dto.RecommendationResponseDTO.RecommendationItemDTO;
import es.cineasee.analitica.repositories.AnalyticsEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lógica de negocio de Analítica.
 *
 * <h2>Fuentes de datos</h2>
 * <ul>
 *   <li><b>Componente 2 (Reservas)</b> — fuente principal de ventas:
 *       {@code GET /reservations/stats} para ocupación y
 *       {@code GET /reservations/box-office-data} para taquilla.</li>
 *   <li><b>Componente 1 (Cartelera)</b> — aforo de salas, títulos y catálogo.</li>
 *   <li><b>Componente 3 (Usuarios)</b> — perfil e historial para recomendaciones.</li>
 *   <li><b>Eventos de RabbitMQ</b> — hechos de venta persistidos en
 *       {@code analytics_events}; se usan para recomendaciones y como respaldo
 *       si Reservas no responde.</li>
 * </ul>
 *
 * <p>Ningún servicio lee la base de datos de otro: todo el cruce se hace en
 * memoria con llamadas HTTP.</p>
 */
@Slf4j
@Service
public class AnalyticsService {

    private static final int DEFAULT_ROOM_CAPACITY = 150;
    private static final int DEFAULT_TOTAL_CAPACITY = 500;
    private static final String DEFAULT_CURRENCY = "EUR";

    private final AnalyticsEventRepository analyticsEventRepository;
    private final CarteleraClient carteleraClient;
    private final UsuariosClient usuariosClient;
    private final ReservasClient reservasClient;
    private final ZoneId zoneId;

    public AnalyticsService(
            AnalyticsEventRepository analyticsEventRepository,
            CarteleraClient carteleraClient,
            UsuariosClient usuariosClient,
            ReservasClient reservasClient,
            @Value("${app.analytics.time-zone:Europe/Madrid}") String timeZone
    ) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.carteleraClient = carteleraClient;
        this.usuariosClient = usuariosClient;
        this.reservasClient = reservasClient;
        this.zoneId = ZoneId.of(timeZone);
    }

    // ------------------------------------------------------------------
    // Ocupación
    // ------------------------------------------------------------------

    /**
     * Calcula la ocupación de una sala (o de todo el cine) cruzando:
     * aforo del Componente 1 + entradas vendidas del Componente 2.
     *
     * <p>El filtro por cine se resuelve con las salas de C1
     * ({@code GET /rooms?cinemaId=}): el Componente 2 devuelve ventas por
     * {@code roomId}, así que primero se obtienen las salas del cine y después
     * se descartan las filas de otras salas.</p>
     *
     * @param cinemaId  cine a analizar; si es {@code null}, no se filtra
     * @param screenId  sala concreta (opcional)
     * @param dayOfWeek 1 = lunes ... 7 = domingo (opcional)
     * @param startTime hora local de inicio de franja, formato HH:mm (opcional)
     * @param endTime   hora local de fin de franja, formato HH:mm (opcional)
     */
    public OccupancyMetricDTO calculateOccupancy(String cinemaId, String screenId, Integer dayOfWeek,
                                                 String startTime, String endTime) {
        List<RoomDTO> cinemaRooms = fetchRooms(cinemaId);
        int totalCapacity = resolveCapacity(cinemaId, screenId, cinemaRooms);
        int totalTicketsSold = resolveTicketsSold(cinemaId, screenId, dayOfWeek, startTime, endTime,
                cinemaRooms);

        double occupancyPercentage = 0.0;
        if (totalCapacity > 0) {
            occupancyPercentage = Math.min(100.0,
                    Math.round(((double) totalTicketsSold / totalCapacity) * 10000.0) / 100.0);
        }

        String timeRange = (startTime != null && endTime != null) ? startTime + "-" + endTime : null;

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

    /**
     * Salas del cine según Cartelera (C1). Devuelve {@code null} si C1 no
     * responde, para distinguir "cine sin salas" (lista vacía) de "no se pudo
     * consultar" (null).
     */
    private List<RoomDTO> fetchRooms(String cinemaId) {
        try {
            return cinemaId != null
                    ? carteleraClient.getRoomsByCinema(cinemaId)
                    : carteleraClient.getAllRooms();
        } catch (Exception ex) {
            log.warn("No se pudieron obtener las salas desde Cartelera: {}. "
                            + "Se usa un aforo estimado y no se filtra la venta por cine.",
                    ex.getMessage());
            return null;
        }
    }

    /** Aforo: sala concreta o suma de las salas del cine. Siempre desde C1. */
    private int resolveCapacity(String cinemaId, String screenId, List<RoomDTO> cinemaRooms) {
        if (screenId != null) {
            RoomDTO room = cinemaRooms == null ? null : cinemaRooms.stream()
                    .filter(r -> screenId.equals(r.getId()))
                    .findFirst()
                    .orElse(null);

            if (room != null) {
                return room.getCapacity() != null ? room.getCapacity() : DEFAULT_ROOM_CAPACITY;
            }

            // La sala no está en la lista del cine (o C1 no respondió): se consulta
            // el detalle para distinguir una sala de otro cine de un fallo de C1.
            try {
                RoomDTO detail = carteleraClient.getRoomById(screenId);
                if (detail == null) {
                    return DEFAULT_ROOM_CAPACITY;
                }
                if (cinemaId != null && detail.getCinemaId() != null
                        && !cinemaId.equals(detail.getCinemaId())) {
                    log.warn("La sala {} no pertenece al cine {}; se devuelve aforo 0.",
                            screenId, cinemaId);
                    return 0;
                }
                return detail.getCapacity() != null ? detail.getCapacity() : DEFAULT_ROOM_CAPACITY;
            } catch (Exception ex) {
                log.warn("No se pudo obtener la sala {} desde Cartelera: {}. "
                        + "Se usa un valor estimado.", screenId, ex.getMessage());
                return DEFAULT_ROOM_CAPACITY;
            }
        }

        if (cinemaRooms != null) {
            return cinemaRooms.stream()
                    .mapToInt(r -> r.getCapacity() != null ? r.getCapacity() : 100)
                    .sum();
        }
        return DEFAULT_TOTAL_CAPACITY;
    }

    /**
     * Entradas vendidas: fuente principal Reservas (C2). Si C2 no responde, se
     * cae a los eventos locales de RabbitMQ para no romper el informe.
     */
    private int resolveTicketsSold(String cinemaId, String screenId, Integer dayOfWeek,
                                   String startTime, String endTime, List<RoomDTO> cinemaRooms) {
        try {
            List<ShowStatsDTO> stats = reservasClient.getShowStats(null, screenId, null, null);
            if (stats == null || stats.isEmpty()) {
                return 0;
            }

            Set<String> cinemaRoomIds = cinemaRoomIds(cinemaId, cinemaRooms);

            long total = stats.stream()
                    .filter(row -> screenId == null || screenId.equals(row.roomId()))
                    .filter(row -> belongsToCinema(row.roomId(), cinemaId, cinemaRoomIds))
                    .filter(row -> matchesDayOfWeek(row.showStartTime(), dayOfWeek))
                    .filter(row -> matchesTimeRange(row.showStartTime(), startTime, endTime))
                    .mapToLong(ShowStatsDTO::ticketsSold)
                    .sum();

            return (int) Math.min(Integer.MAX_VALUE, total);
        } catch (Exception ex) {
            log.warn("No se pudo obtener la venta desde Reservas: {}. Se usan los eventos locales.",
                    ex.getMessage());
            Long tickets = analyticsEventRepository.sumTicketsSold(cinemaId, screenId, dayOfWeek);
            return tickets != null ? tickets.intValue() : 0;
        }
    }

    /**
     * IDs de las salas del cine. {@code null} significa que no se puede filtrar
     * (no hay cine o C1 no respondió); un conjunto vacío significa que el cine
     * no tiene salas y, por tanto, no puede tener ventas.
     */
    private Set<String> cinemaRoomIds(String cinemaId, List<RoomDTO> cinemaRooms) {
        if (cinemaId == null || cinemaRooms == null) {
            return null;
        }
        return cinemaRooms.stream()
                .map(RoomDTO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean belongsToCinema(String roomId, String cinemaId, Set<String> cinemaRoomIds) {
        if (cinemaId == null || cinemaRoomIds == null) {
            return true;
        }
        return roomId != null && cinemaRoomIds.contains(roomId);
    }

    private boolean matchesDayOfWeek(Instant showStartTime, Integer dayOfWeek) {
        if (dayOfWeek == null || showStartTime == null) {
            return true;
        }
        return showStartTime.atZone(zoneId).getDayOfWeek().getValue() == dayOfWeek;
    }

    private boolean matchesTimeRange(Instant showStartTime, String startTime, String endTime) {
        if (showStartTime == null || (!hasText(startTime) && !hasText(endTime))) {
            return true;
        }

        LocalTime localStart = showStartTime.atZone(zoneId).toLocalTime();
        try {
            if (hasText(startTime) && localStart.isBefore(LocalTime.parse(startTime))) {
                return false;
            }
            if (hasText(endTime) && localStart.isAfter(LocalTime.parse(endTime))) {
                return false;
            }
            return true;
        } catch (DateTimeParseException ex) {
            log.warn("Franja horaria no válida ({}-{}): {}. Se ignora el filtro.",
                    startTime, endTime, ex.getMessage());
            return true;
        }
    }

    // ------------------------------------------------------------------
    // Box Office
    // ------------------------------------------------------------------

    /**
     * Informe de taquilla. Los tickets e ingresos vienen del Componente 2;
     * el título se resuelve con el catálogo del Componente 1.
     */
    public BoxOfficeReportDTO generateBoxOfficeReport(Long distributorId, String movieId,
                                                      LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio (startDate) no puede ser posterior a la fecha de fin (endDate)");
        }

        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        try {
            BoxOfficeDataDTO data = reservasClient.getBoxOfficeData(start, end, movieId);
            return buildReportFromReservations(distributorId, movieId, start, end, data);
        } catch (Exception ex) {
            log.warn("No se pudo obtener la taquilla desde Reservas: {}. Se usan los eventos locales.",
                    ex.getMessage());
            return buildReportFromEvents(distributorId, movieId, start, end);
        }
    }

    private BoxOfficeReportDTO buildReportFromReservations(Long distributorId, String movieId,
                                                           LocalDate start, LocalDate end,
                                                           BoxOfficeDataDTO data) {
        List<BoxOfficeDataDTO.MovieBoxOfficeDTO> rows =
                (data != null && data.movies() != null) ? data.movies() : List.of();

        if (distributorId != null) {
            // El catálogo (C1) guarda la distribuidora como texto ("Warner Bros."),
            // no como id numérico. No se inventa una correspondencia: se deja
            // constancia de que el filtro no se puede aplicar con los datos actuales.
            log.warn("distributorId={} no se puede aplicar: el catálogo no expone ids de distribuidora",
                    distributorId);
        }

        long totalTicketsSold = 0L;
        BigDecimal grossRevenue = BigDecimal.ZERO;

        for (BoxOfficeDataDTO.MovieBoxOfficeDTO row : rows) {
            totalTicketsSold += row.totalTicketsSold();
            if (row.grossRevenue() != null) {
                grossRevenue = grossRevenue.add(row.grossRevenue());
            }
        }

        String currency = (data != null && data.currency() != null)
                ? data.currency()
                : DEFAULT_CURRENCY;

        return BoxOfficeReportDTO.builder()
                .distributorId(distributorId)
                .movieId(movieId)
                .movieTitle(resolveMovieTitle(movieId))
                .period(BoxOfficeReportDTO.PeriodDTO.builder()
                        .startDate(start)
                        .endDate(end)
                        .build())
                .totalTicketsSold(totalTicketsSold)
                .grossRevenue(grossRevenue.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .currency(currency)
                .build();
    }

    /** Respaldo: cálculo a partir de los eventos de RabbitMQ persistidos en C4. */
    private BoxOfficeReportDTO buildReportFromEvents(Long distributorId, String movieId,
                                                     LocalDate start, LocalDate end) {
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

        if (resolvedTitle == null) {
            resolvedTitle = resolveMovieTitle(movieId);
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
                .currency(DEFAULT_CURRENCY)
                .build();
    }

    private String resolveMovieTitle(String movieId) {
        if (movieId == null) {
            return "Resumen General Box Office";
        }
        try {
            MovieDTO movie = carteleraClient.getMovieById(movieId);
            if (movie != null && movie.getTitle() != null) {
                return movie.getTitle();
            }
        } catch (Exception ex) {
            log.warn("No se pudo obtener el título de la película {}: {}", movieId, ex.getMessage());
        }
        return "Película #" + movieId;
    }

    // ------------------------------------------------------------------
    // Recomendaciones
    // ------------------------------------------------------------------

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

        Set<String> watchedMovieIds = history.stream()
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
            if (movie.getId() == null) {
                continue;
            }

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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
