package com.cinasee.componente1_cartelera.config;

import com.cinasee.componente1_cartelera.models.Cinema;
import com.cinasee.componente1_cartelera.models.Movie;
import com.cinasee.componente1_cartelera.models.Room;
import com.cinasee.componente1_cartelera.models.Session;
import com.cinasee.componente1_cartelera.repositories.CinemaRepository;
import com.cinasee.componente1_cartelera.repositories.MovieRepository;
import com.cinasee.componente1_cartelera.repositories.RoomRepository;
import com.cinasee.componente1_cartelera.repositories.SessionRepository;
import com.cinasee.componente1_cartelera.services.MovieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogSeeder implements CommandLineRunner {

    private final CinemaRepository cinemaRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final SessionRepository sessionRepository;

    @Value("${app.media.base-url:http://localhost:8080/media/posters}")
    private String mediaBaseUrl;

    /** Días de cartelera de ejemplo que se crean desde el primer arranque */
    private static final int DIAS_DE_CARTELERA = 14;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Override
    public void run(String... args) {
        log.info("Ejecutando seed idempotente del catálogo de CinAsee...");

        List<Cinema> cinemas = buildCinemas();
        cinemas.forEach(c -> {
            if (!cinemaRepository.existsById(c.getId())) {
                cinemaRepository.save(c);
            }
        });

        List<Movie> movies = buildMovies();
        movies.forEach(m -> {
            if (!movieRepository.existsById(m.getId())) {
                movieRepository.save(m);
            }
        });
        corregirPeliculasExistentes();

        List<Room> rooms = buildRooms();
        rooms.forEach(r -> {
            if (!roomRepository.existsById(r.getId())) {
                roomRepository.save(r);
            }
        });

        Map<String, Movie> movieById = movies.stream()
                .collect(Collectors.toMap(Movie::getId, Function.identity()));

        List<Session> sessions = buildSessions(movieById);
        int insertadas = 0;
        int saltadas = 0;
        for (Session s : sessions) {
            if (sessionRepository.existsById(s.getId())) {
                continue;
            }
            // No se inserta una sesión de ejemplo que choque con otra ya existente en la sala
            if (seSolapa(s)) {
                saltadas++;
                continue;
            }
            sessionRepository.save(s);
            insertadas++;
        }
        if (saltadas > 0) {
            log.info("{} sesiones de ejemplo no se han creado porque chocaban con otras de la misma sala.", saltadas);
        }

        log.info("Seed de catálogo verificado ({} cines, {} películas, {} salas, {} sesiones de ejemplo; {} nuevas).",
                cinemas.size(), movies.size(), rooms.size(), sessions.size(), insertadas);
    }

    /**
     * Las bases creadas con el seed anterior guardaban géneros con tilde ("ANIMACIÓN") y la
     * edad "+0". Como el seed no modifica documentos existentes, aquí se corrigen esos valores
     * con las mismas reglas que al crear o editar una película. Es idempotente.
     */
    private void corregirPeliculasExistentes() {
        int corregidas = 0;
        for (Movie movie : movieRepository.findAll()) {
            String genero = MovieService.normalizarGenero(movie.getGenre());
            String edad = MovieService.normalizarEdad(movie.getAgeRating());
            boolean cambia = !Objects.equals(genero, movie.getGenre())
                    || !Objects.equals(edad, movie.getAgeRating());
            if (cambia) {
                movie.setGenre(genero);
                movie.setAgeRating(edad);
                movieRepository.save(movie);
                corregidas++;
            }
        }
        if (corregidas > 0) {
            log.info("Corregidos el género o la edad de {} películas ya existentes.", corregidas);
        }
    }

    private List<Cinema> buildCinemas() {
        return List.of(
            cinema("550e8400-e29b-41d4-a716-446655440001", "CinAsee Madrid", "Gran Vía 1", "Madrid", "910000001"),
            cinema("550e8400-e29b-41d4-a716-446655440002", "CinAsee Barcelona", "Passeig de Gràcia 2", "Barcelona", "930000002"),
            cinema("550e8400-e29b-41d4-a716-446655440003", "CinAsee Cáceres", "Avenida de España 12", "Cáceres", "927000003"),
            cinema("550e8400-e29b-41d4-a716-446655440004", "CinAsee Navatrasierra", "Calle Real 5", "Navatrasierra", "927000004"),
            cinema("550e8400-e29b-41d4-a716-446655440005", "CinAsee Madroñera", "Plaza Mayor 3", "Madroñera", "927000005"),
            cinema("550e8400-e29b-41d4-a716-446655440006", "CinAsee Málaga", "Calle Marqués de Larios 8", "Málaga", "952000006"),
            cinema("550e8400-e29b-41d4-a716-446655440007", "CinAsee Cuenca", "Calle Carretería 14", "Cuenca", "969000007")
        );
    }

    private List<Movie> buildMovies() {
        return List.of(
            movie("00000000-0000-4000-8000-000000000101", "Ponyo en el acantilado",
                "Ponyo, una pez mágica que sueña con convertirse en humana, conoce a un niño llamado Sosuke. Su amistad desencadena una aventura que pondrá en peligro el equilibrio entre el mar y la tierra.",
                101, "ANIMACION", "TP", "ponyo.jpg", "https://www.youtube.com/watch?v=CsR3KVgBzSM"),
            movie("00000000-0000-4000-8000-000000000102", "El castillo ambulante",
                "La joven Sophie es víctima de una maldición que la convierte en una anciana. En su búsqueda por romper el hechizo, encuentra refugio en el misterioso castillo ambulante del mago Howl, donde deberá enfrentarse a una guerra, descubrir secretos mágicos y encontrar su verdadera fortaleza.",
                119, "FANTASIA", "+7", "castillo-ambulante.jpg", "https://www.youtube.com/watch?v=iwROgK94zcM"),
            movie("00000000-0000-4000-8000-000000000103", "Todo a la vez en todas partes",
                "Evelyn, una inmigrante china que dirige una lavandería, descubre que debe conectar con versiones alternativas de sí misma a través del multiverso para detener una amenaza que podría destruir todas las realidades.",
                139, "CIENCIA_FICCION", "+16", "todo-a-la-vez.jpg", "https://youtu.be/U3cKYWgl2dU?si=OAk3oCJ-g4xQc5xl"),
            movie("00000000-0000-4000-8000-000000000104", "Interstellar",
                "Un grupo de exploradores viaja a través de un agujero de gusano en el espacio en un intento por garantizar la supervivencia de la humanidad.",
                169, "CIENCIA_FICCION", "+12", "interstellar.jpg", "https://youtu.be/UoSSbmD9vqc?si=IfXv_XxghC1jfzzt"),
            movie("00000000-0000-4000-8000-000000000105", "Orgullo y prejuicio",
                "En la Inglaterra del siglo XVIII, las cinco hermanas Bennet lidian con cuestiones de matrimonio, moralidad y prejuicios cuando el adinerado Sr. Bingley y el orgulloso Sr. Darcy llegan a su vecindario.",
                129, "ROMANCE", "TP", "orgullo-y-prejuicio.jpg", "https://www.youtube.com/watch?v=1dYv5u6v55Y"),
            movie("00000000-0000-4000-8000-000000000106", "Harry Potter y el prisionero de Azkaban",
                "En su tercer año en Hogwarts, Harry Potter se enfrenta a la amenaza de Sirius Black, un peligroso prisionero que ha escapado de la fortaleza de Azkaban y parece ir tras él.",
                142, "FANTASIA", "+7", "harry-potter-azkaban.jpg", "https://www.youtube.com/watch?v=lAxgztbYDbs"),
            movie("00000000-0000-4000-8000-000000000107", "The VelociPastor",
                "Tras perder a sus padres, un sacerdote viaja a China, donde hereda una misteriosa habilidad que le permite convertirse en dinosaurio para luchar contra el crimen y los ninjas.",
                75, "COMEDIA", "+16", "velocipastor.jpg", "https://youtu.be/D7wwoCBtvaA?si=QzRVMl1EPrNtQ8AF"),
            movie("00000000-0000-4000-8000-000000000108", "Los juegos del hambre: Amanecer en la cosecha",
                "Veinticuatro años antes de los eventos de la trilogía original, en la mañana de la cosecha de los Quincuagésimos Juegos del Hambre, un joven Haymitch Abernathy es seleccionado como tributo del Distrito 12 para el Segundo Vasallaje de los Veinticinco.",
                145, "CIENCIA_FICCION", "+12", "juegos-del-hambre.jpg", "https://youtu.be/6jWXR47FCUQ?si=XLlzjCMqx1-nozNd")
        );
    }

    private List<Room> buildRooms() {
        return List.of(
            room("00000000-0000-4000-8000-000000000201", "550e8400-e29b-41d4-a716-446655440001", "Sala 1 - Estándar", "ESTANDAR_2D", 10, 12, 8.5),
            room("00000000-0000-4000-8000-000000000202", "550e8400-e29b-41d4-a716-446655440001", "Sala 2 - 3D", "DIGITAL_3D", 8, 10, 10.5),
            room("00000000-0000-4000-8000-000000000203", "550e8400-e29b-41d4-a716-446655440001", "Sala 3 - VIP", "VIP", 6, 8, 14.0),
            room("00000000-0000-4000-8000-000000000204", "550e8400-e29b-41d4-a716-446655440002", "Sala 1 - Estándar", "ESTANDAR_2D", 10, 12, 8.5),
            room("00000000-0000-4000-8000-000000000205", "550e8400-e29b-41d4-a716-446655440002", "Sala 2 - 3D", "DIGITAL_3D", 8, 10, 10.5),
            room("00000000-0000-4000-8000-000000000206", "550e8400-e29b-41d4-a716-446655440003", "Sala 1 - Estándar", "ESTANDAR_2D", 9, 10, 8.0),
            room("00000000-0000-4000-8000-000000000207", "550e8400-e29b-41d4-a716-446655440003", "Sala 2 - 3D", "DIGITAL_3D", 7, 10, 10.0),
            room("00000000-0000-4000-8000-000000000208", "550e8400-e29b-41d4-a716-446655440004", "Sala 1 - Estándar", "ESTANDAR_2D", 6, 8, 7.0),
            room("00000000-0000-4000-8000-000000000209", "550e8400-e29b-41d4-a716-446655440005", "Sala 1 - Estándar", "ESTANDAR_2D", 6, 8, 7.0),
            room("00000000-0000-4000-8000-000000000210", "550e8400-e29b-41d4-a716-446655440006", "Sala 1 - Estándar", "ESTANDAR_2D", 10, 12, 8.5),
            room("00000000-0000-4000-8000-000000000211", "550e8400-e29b-41d4-a716-446655440006", "Sala 2 - 3D", "DIGITAL_3D", 8, 10, 10.5),
            room("00000000-0000-4000-8000-000000000212", "550e8400-e29b-41d4-a716-446655440007", "Sala 1 - Estándar", "ESTANDAR_2D", 8, 10, 8.0)
        );
    }

    /** true si la sesión se cruza con otra no cancelada de la misma sala */
    private boolean seSolapa(Session nueva) {
        return sessionRepository.findByRoomIdAndStatusNot(nueva.getRoomId(), "CANCELADA").stream()
                .anyMatch(s -> s.getStartTime() != null && s.getEndTime() != null
                        && s.getStartTime().isBefore(nueva.getEndTime())
                        && s.getEndTime().isAfter(nueva.getStartTime()));
    }

    /**
     * Sesiones de ejemplo para {@value #DIAS_DE_CARTELERA} días desde el primer arranque, para
     * que haya cartelera aunque se pruebe la aplicación días después de levantarla.
     * Los días 1 a 3 son la programación base; del día 4 en adelante se repiten los horarios
     * del día 1 y del día 2 (alternos), que cubren todos los cines. Cada copia tiene un id fijo
     * (el de la sesión original con el número de día), así que el seed sigue siendo idempotente.
     */
    private List<Session> buildSessions(Map<String, Movie> movieById) {
        List<Session> base = buildSesionesBase(movieById);
        List<Session> todas = new ArrayList<>(base);
        LocalDate hoy = LocalDate.now(MADRID);
        for (int dia = 4; dia <= DIAS_DE_CARTELERA; dia++) {
            int diaPlantilla = dia % 2 == 0 ? 2 : 1;
            LocalDate fechaPlantilla = hoy.plusDays(diaPlantilla);
            for (Session original : base) {
                if (!original.getStartTime().toLocalDate().equals(fechaPlantilla)) {
                    continue;
                }
                Session copia = new Session();
                copia.setId(idDeCopia(original.getId(), dia));
                copia.setMovieId(original.getMovieId());
                copia.setRoomId(original.getRoomId());
                copia.setStartTime(original.getStartTime().plusDays(dia - diaPlantilla));
                copia.setEndTime(original.getEndTime().plusDays(dia - diaPlantilla));
                copia.setStatus(original.getStatus());
                copia.setTicketPrice(original.getTicketPrice());
                todas.add(copia);
            }
        }
        return todas;
    }

    /** "01M7SR8QHR0000000000000001" del día 7 -> "01M7SR8QHS0700000000000001" (sigue siendo un ULID válido) */
    static String idDeCopia(String idOriginal, int dia) {
        return "01M7SR8QHS" + String.format("%02d", dia) + idOriginal.substring(12);
    }

    /** Programación base: 48 sesiones repartidas en los 3 primeros días */
    private List<Session> buildSesionesBase(Map<String, Movie> movieById) {
        String mPonyo = "00000000-0000-4000-8000-000000000101";
        String mHowl = "00000000-0000-4000-8000-000000000102";
        String mEeaao = "00000000-0000-4000-8000-000000000103";
        String mInter = "00000000-0000-4000-8000-000000000104";
        String mPride = "00000000-0000-4000-8000-000000000105";
        String mHp = "00000000-0000-4000-8000-000000000106";
        String mVeloci = "00000000-0000-4000-8000-000000000107";
        String mHunger = "00000000-0000-4000-8000-000000000108";

        String rMad1 = "00000000-0000-4000-8000-000000000201";
        String rMad2 = "00000000-0000-4000-8000-000000000202";
        String rMad3 = "00000000-0000-4000-8000-000000000203";
        String rBcn1 = "00000000-0000-4000-8000-000000000204";
        String rBcn2 = "00000000-0000-4000-8000-000000000205";
        String rCac1 = "00000000-0000-4000-8000-000000000206";
        String rCac2 = "00000000-0000-4000-8000-000000000207";
        String rNav1 = "00000000-0000-4000-8000-000000000208";
        String rMdr1 = "00000000-0000-4000-8000-000000000209";
        String rMal1 = "00000000-0000-4000-8000-000000000210";
        String rMal2 = "00000000-0000-4000-8000-000000000211";
        String rCue1 = "00000000-0000-4000-8000-000000000212";

        return List.of(
            session("01M7SR8QHR0000000000000001", 1, 16, 0, mVeloci, rMad1, 8.5, movieById),
            session("01M7SR8QHR0000000000000002", 1, 19, 0, mInter, rMad1, 8.5, movieById),
            session("01M7SR8QHR0000000000000003", 1, 17, 0, mPonyo, rMad2, 10.5, movieById),
            session("01M7SR8QHR0000000000000004", 1, 20, 0, mHunger, rMad2, 10.5, movieById),
            session("01M7SR8QHR0000000000000005", 1, 17, 30, mHowl, rMad3, 14.0, movieById),
            session("01M7SR8QHR0000000000000006", 1, 20, 0, mEeaao, rMad3, 14.0, movieById),
            session("01M7SR8QHR0000000000000007", 2, 16, 0, mHowl, rMad1, 8.5, movieById),
            session("01M7SR8QHR0000000000000008", 2, 19, 0, mPride, rMad1, 8.5, movieById),
            session("01M7SR8QHR0000000000000009", 2, 17, 0, mVeloci, rMad2, 10.5, movieById),
            session("01M7SR8QHR000000000000000A", 2, 20, 0, mInter, rMad2, 10.5, movieById),
            session("01M7SR8QHR000000000000000B", 2, 17, 30, mPonyo, rMad3, 14.0, movieById),
            session("01M7SR8QHR000000000000000C", 2, 20, 0, mHp, rMad3, 14.0, movieById),
            session("01M7SR8QHR000000000000000D", 3, 16, 0, mPonyo, rMad1, 8.5, movieById),
            session("01M7SR8QHR000000000000000E", 3, 19, 0, mEeaao, rMad1, 8.5, movieById),
            session("01M7SR8QHR000000000000000F", 3, 17, 0, mPride, rMad2, 10.5, movieById),
            session("01M7SR8QHR000000000000000G", 3, 20, 0, mHp, rMad2, 10.5, movieById),
            session("01M7SR8QHR000000000000000H", 3, 17, 30, mVeloci, rMad3, 14.0, movieById),
            session("01M7SR8QHR000000000000000J", 3, 20, 0, mHunger, rMad3, 14.0, movieById),

            session("01M7SR8QHR000000000000000K", 1, 16, 30, mPonyo, rBcn1, 8.5, movieById),
            session("01M7SR8QHR000000000000000M", 1, 19, 30, mHunger, rBcn1, 8.5, movieById),
            session("01M7SR8QHR000000000000000N", 1, 17, 0, mHowl, rBcn2, 10.5, movieById),
            session("01M7SR8QHR000000000000000P", 1, 20, 0, mInter, rBcn2, 10.5, movieById),
            session("01M7SR8QHR000000000000000Q", 2, 17, 0, mHp, rBcn1, 8.5, movieById),
            session("01M7SR8QHR000000000000000R", 2, 20, 15, mEeaao, rBcn1, 8.5, movieById),
            session("01M7SR8QHR000000000000000S", 2, 18, 0, mVeloci, rBcn2, 10.5, movieById),
            session("01M7SR8QHR000000000000000T", 2, 20, 0, mPride, rBcn2, 10.5, movieById),

            session("01M7SR8QHR000000000000000V", 1, 17, 0, mPride, rCac1, 8.0, movieById),
            session("01M7SR8QHR000000000000000W", 1, 20, 0, mHp, rCac1, 8.0, movieById),
            session("01M7SR8QHR000000000000000X", 1, 18, 0, mEeaao, rCac2, 10.0, movieById),
            session("01M7SR8QHR000000000000000Y", 2, 17, 30, mPonyo, rCac1, 8.0, movieById),
            session("01M7SR8QHR000000000000000Z", 2, 20, 0, mInter, rCac1, 8.0, movieById),
            session("01M7SR8QHR0000000000000010", 2, 18, 30, mHunger, rCac2, 10.0, movieById),

            session("01M7SR8QHR0000000000000011", 1, 18, 0, mPonyo, rNav1, 7.0, movieById),
            session("01M7SR8QHR0000000000000012", 1, 20, 30, mVeloci, rNav1, 7.0, movieById),
            session("01M7SR8QHR0000000000000013", 2, 19, 0, mHowl, rNav1, 7.0, movieById),

            session("01M7SR8QHR0000000000000014", 1, 18, 30, mPride, rMdr1, 7.0, movieById),
            session("01M7SR8QHR0000000000000015", 2, 17, 30, mVeloci, rMdr1, 7.0, movieById),
            session("01M7SR8QHR0000000000000016", 2, 19, 30, mHunger, rMdr1, 7.0, movieById),

            session("01M7SR8QHR0000000000000017", 1, 16, 30, mHowl, rMal1, 8.5, movieById),
            session("01M7SR8QHR0000000000000018", 1, 19, 30, mEeaao, rMal1, 8.5, movieById),
            session("01M7SR8QHR0000000000000019", 1, 17, 30, mHp, rMal2, 10.5, movieById),
            session("01M7SR8QHR000000000000001A", 1, 20, 45, mInter, rMal2, 10.5, movieById),
            session("01M7SR8QHR000000000000001B", 2, 17, 0, mPonyo, rMal1, 8.5, movieById),
            session("01M7SR8QHR000000000000001C", 2, 19, 30, mPride, rMal1, 8.5, movieById),
            session("01M7SR8QHR000000000000001D", 2, 18, 0, mHunger, rMal2, 10.5, movieById),

            session("01M7SR8QHR000000000000001E", 1, 17, 0, mPonyo, rCue1, 8.0, movieById),
            session("01M7SR8QHR000000000000001F", 1, 19, 30, mInter, rCue1, 8.0, movieById),
            session("01M7SR8QHR000000000000001G", 2, 18, 0, mHp, rCue1, 8.0, movieById)
        );
    }

    private Cinema cinema(String id, String name, String address, String city, String phone) {
        Cinema c = new Cinema();
        c.setId(id);
        c.setName(name);
        c.setAddress(address);
        c.setCity(city);
        c.setPhone(phone);
        return c;
    }

    private Movie movie(String id, String title, String synopsis, int duration,
                        String genre, String ageRating, String posterFile, String trailerUrl) {
        Movie m = new Movie();
        m.setId(id);
        m.setTitle(title);
        m.setSynopsis(synopsis);
        m.setDurationMinutes(duration);
        m.setGenre(genre);
        m.setAgeRating(ageRating);
        m.setPosterUrl(mediaBaseUrl + "/" + posterFile);
        m.setTrailerUrl(trailerUrl);
        return m;
    }

    private Room room(String id, String cinemaId, String name, String roomType,
                      int rows, int cols, double basePrice) {
        Room r = new Room();
        r.setId(id);
        r.setCinemaId(cinemaId);
        r.setName(name);
        r.setRoomType(roomType);
        r.setRowsCount(rows);
        r.setColumnsCount(cols);
        r.setCapacity(rows * cols);
        r.setWheelchairAccessible(true);
        r.setBasePrice(basePrice);
        return r;
    }

    private Session session(String id, int daysFromToday, int hour, int minute,
                            String movieId, String roomId, double ticketPrice,
                            Map<String, Movie> movieById) {
        Movie movie = movieById.get(movieId);
        LocalDateTime startTime = LocalDateTime.now(MADRID)
                .plusDays(daysFromToday)
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);

        Session s = new Session();
        s.setId(id);
        s.setMovieId(movieId);
        s.setRoomId(roomId);
        s.setStartTime(startTime);
        s.setEndTime(startTime.plusMinutes(movie.getDurationMinutes() + 20L));
        s.setStatus("A_LA_VENTA");
        s.setTicketPrice(ticketPrice);
        return s;
    }
}

